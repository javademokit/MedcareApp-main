package com.medora.assistant.appointment.workflow;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node 2: Data Extractor
 * Extracts structured data from the user's message.
 * Returns Map<String, Object> state updates for LangGraph4j.
 */
public class DataExtractorNode {

    private static final Logger log = LoggerFactory.getLogger(DataExtractorNode.class);

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4}-\\d{2}-\\d{2})|((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\.?\\s+\\d{1,2})|" +
        "(tomorrow)|(today)|(next\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|week))",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(\\d{1,2}:\\d{2}\\s*(?:am|pm)?)|(\\d{1,2}\\s*(?:am|pm))",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DOCTOR_PATTERN = Pattern.compile(
        "(?:dr\\.?\\s+)([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)",
        Pattern.CASE_INSENSITIVE
    );

    // Patient name patterns: "for John Doe", "my name is John", "I am John", "John Doe wants"
    // Stop capturing at common boundary words like "with", "for", "on", "at", "to", "by"
    private static final Pattern PATIENT_NAME_PATTERN = Pattern.compile(
        "(?:for|my\\s+name\\s+is|I\\s+am|I'm|name\\s+is|patient\\s+is|patient\\s+name\\s+is)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ID_PATTERN = Pattern.compile(
        "(?:#|id|appointment\\s+)(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    private final ChatLanguageModel chatModel;

    public DataExtractorNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * LangGraph4j NodeAction - extract data and return state updates.
     * If the user is answering a follow-up question (short response), intelligently
     * maps the response to the first missing field.
     */
    public Map<String, Object> extract(AppointmentState state) {
        log.info("DataExtractor: Extracting data for intent: {}", state.getIntent());

        String message = state.getUserMessage();
        if (message == null || message.isBlank()) {
            return Map.of(AppointmentState.STEP_COUNT, state.getStepCount() + 1);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(AppointmentState.STEP_COUNT, state.getStepCount() + 1);

        // Identify what's missing BEFORE extraction, to help interpret short responses
        List<String> missingBefore = identifyMissingFields(state, new HashMap<>());

        // Extract using rules first
        extractByRules(message, state, updates);

        // If the message is short (likely a follow-up answer) and we still have missing fields,
        // try to map it to the first missing field
        if (message.split("\\s+").length <= 5 && missingBefore.size() >= 1) {
            String firstMissing = missingBefore.get(0).toLowerCase();
            if (firstMissing.equals("patient name") && !updates.containsKey(AppointmentState.PATIENT_NAME)) {
                // Treat the entire message as the patient name
                String name = message.trim();
                // Clean up common prefixes
                name = name.replaceFirst("(?i)^(my\\s+name\\s+is|I\\s+am|I'm|name\\s+is)\\s+", "");
                if (name.length() >= 2 && !name.matches("\\d.*")) {
                    updates.put(AppointmentState.PATIENT_NAME, name);
                    log.info("DataExtractor: Mapped short response to patient name: {}", name);
                }
            } else if (firstMissing.equals("doctor name") && !updates.containsKey(AppointmentState.DOCTOR_NAME)) {
                String doctor = message.trim();
                if (!doctor.toLowerCase().startsWith("dr.")) {
                    doctor = "Dr. " + doctor;
                }
                updates.put(AppointmentState.DOCTOR_NAME, doctor);
                log.info("DataExtractor: Mapped short response to doctor name: {}", doctor);
            } else if (firstMissing.equals("appointment date") && !updates.containsKey(AppointmentState.APPOINTMENT_DATE)) {
                String date = normalizeDate(message.trim());
                if (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    updates.put(AppointmentState.APPOINTMENT_DATE, date);
                    log.info("DataExtractor: Mapped short response to date: {}", date);
                }
            } else if (firstMissing.equals("appointment time") && !updates.containsKey(AppointmentState.APPOINTMENT_TIME)) {
                String time = normalizeTime(message.trim());
                if (time != null && time.matches("\\d{2}:\\d{2}")) {
                    updates.put(AppointmentState.APPOINTMENT_TIME, time);
                    log.info("DataExtractor: Mapped short response to time: {}", time);
                }
            } else if (firstMissing.equals("reason") && !updates.containsKey(AppointmentState.REASON)) {
                updates.put(AppointmentState.REASON, message.trim());
                log.info("DataExtractor: Mapped short response to reason: {}", message.trim());
            }
        }

        // Then try LLM extraction for any remaining missing fields
        extractByLLM(message, state, updates);

        // Identify missing fields
        List<String> missing = identifyMissingFields(state, updates);
        updates.put(AppointmentState.MISSING_FIELDS, missing);
        updates.put(AppointmentState.NEEDS_FOLLOW_UP, !missing.isEmpty());

        log.info("DataExtractor: Extracted - patient={}, doctor={}, date={}, time={}, reason={}",
            getOr(state, AppointmentState.PATIENT_NAME, updates),
            getOr(state, AppointmentState.DOCTOR_NAME, updates),
            getOr(state, AppointmentState.APPOINTMENT_DATE, updates),
            getOr(state, AppointmentState.APPOINTMENT_TIME, updates),
            getOr(state, AppointmentState.REASON, updates));

        return updates;
    }

    private String getOr(AppointmentState state, String key, Map<String, Object> updates) {
        if (updates.containsKey(key)) return (String) updates.get(key);
        return state.get(key);
    }

    private void extractByRules(String message, AppointmentState state, Map<String, Object> updates) {
        // Extract patient name first
        Matcher patientMatcher = PATIENT_NAME_PATTERN.matcher(message);
        if (patientMatcher.find() && state.getPatientName() == null) {
            String name = patientMatcher.group(1).trim();
            // Clean up common prefixes
            name = name.replaceFirst("(?i)^(for|my\\s+name\\s+is|I\\s+am|I'm|name\\s+is)\\s+", "");
            // Remove trailing boundary words and any word after them (e.g., "with Dr", "for tomorrow")
            name = name.replaceAll("(?i)\\s+(?:with|for|on|at|to|by|and|see)\\s+(?:[A-Z][a-z]+)?\\s*$", "").trim();
            // Also remove standalone boundary words at the end
            name = name.replaceAll("(?i)\\s+(?:with|for|on|at|to|by|and|see)\\s*$", "").trim();
            updates.put(AppointmentState.PATIENT_NAME, name);
            log.info("DataExtractor: Extracted patient name by rules: {}", name);
        }

        Matcher doctorMatcher = DOCTOR_PATTERN.matcher(message);
        if (doctorMatcher.find() && state.getDoctorName() == null) {
            String doctorName = doctorMatcher.group(1).trim();
            // Remove trailing boundary words and any word after them (e.g., "for tomorrow", "at 10am")
            doctorName = doctorName.replaceAll("(?i)\\s+(?:for|on|at|to|by|and|see)\\s+(?:[A-Z][a-z]+)?\\s*$", "").trim();
            // Also remove standalone boundary words at the end
            doctorName = doctorName.replaceAll("(?i)\\s+(?:for|on|at|to|by|and|see)\\s*$", "").trim();
            updates.put(AppointmentState.DOCTOR_NAME, "Dr. " + doctorName);
        }

        Matcher dateMatcher = DATE_PATTERN.matcher(message);
        if (dateMatcher.find() && state.getAppointmentDate() == null) {
            updates.put(AppointmentState.APPOINTMENT_DATE, normalizeDate(dateMatcher.group()));
        }

        Matcher timeMatcher = TIME_PATTERN.matcher(message);
        if (timeMatcher.find() && state.getAppointmentTime() == null) {
            updates.put(AppointmentState.APPOINTMENT_TIME, normalizeTime(timeMatcher.group()));
        }

        Matcher idMatcher = ID_PATTERN.matcher(message);
        if (idMatcher.find() && state.getAppointmentId() == null) {
            try {
                updates.put(AppointmentState.APPOINTMENT_ID, Long.parseLong(idMatcher.group(1)));
            } catch (NumberFormatException e) {
                log.warn("DataExtractor: Invalid appointment ID: {}", idMatcher.group(1));
            }
        }
    }

    private void extractByLLM(String message, AppointmentState state, Map<String, Object> updates) {
        try {
            String prompt = buildExtractionPrompt(message, state.getIntent());
            String response = chatModel.generate(Arrays.asList(new UserMessage(prompt))).content().text().trim();
            log.info("DataExtractor LLM response: {}", response);

            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim().toLowerCase();
                    String value = parts[1].trim();
                    if (value.isEmpty() || value.equalsIgnoreCase("null")) continue;

                    switch (key) {
                        case "patient_name", "patientname":
                            if (state.getPatientName() == null && !updates.containsKey(AppointmentState.PATIENT_NAME))
                                updates.put(AppointmentState.PATIENT_NAME, value);
                            break;
                        case "doctor_name", "doctorname":
                            if (state.getDoctorName() == null && !updates.containsKey(AppointmentState.DOCTOR_NAME))
                                updates.put(AppointmentState.DOCTOR_NAME, value.startsWith("Dr.") ? value : "Dr. " + value);
                            break;
                        case "date", "appointment_date":
                            if (state.getAppointmentDate() == null && !updates.containsKey(AppointmentState.APPOINTMENT_DATE))
                                updates.put(AppointmentState.APPOINTMENT_DATE, normalizeDate(value));
                            break;
                        case "time", "appointment_time":
                            if (state.getAppointmentTime() == null && !updates.containsKey(AppointmentState.APPOINTMENT_TIME)) {
                                // Clean up LLM responses like "HH:MM=10:00" or "10:00 (HH:MM)"
                                String cleanTime = value.replaceAll(".*=", "").replaceAll("\\(.*\\)", "").trim();
                                updates.put(AppointmentState.APPOINTMENT_TIME, normalizeTime(cleanTime));
                            }
                            break;
                        case "reason":
                            // Don't treat date/time words as reasons
                            if (state.getReason() == null && !updates.containsKey(AppointmentState.REASON)) {
                                String reasonLower = value.toLowerCase();
                                boolean isDateWord = reasonLower.equals("tomorrow") || reasonLower.equals("today") ||
                                    reasonLower.equals("yesterday") || reasonLower.contains("monday") ||
                                    reasonLower.contains("tuesday") || reasonLower.contains("wednesday") ||
                                    reasonLower.contains("thursday") || reasonLower.contains("friday") ||
                                    reasonLower.contains("saturday") || reasonLower.contains("sunday") ||
                                    reasonLower.matches("\\d{4}-\\d{2}-\\d{2}") ||
                                    reasonLower.matches("\\d{1,2}:\\d{2}.*");
                                if (!isDateWord) {
                                    updates.put(AppointmentState.REASON, value);
                                }
                            }
                            break;
                        case "appointment_id", "id":
                            if (state.getAppointmentId() == null && !updates.containsKey(AppointmentState.APPOINTMENT_ID)) {
                                try {
                                    updates.put(AppointmentState.APPOINTMENT_ID, Long.parseLong(value.replaceAll("[^0-9]", "")));
                                } catch (NumberFormatException e) { /* ignore */ }
                            }
                            break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("DataExtractor: LLM extraction failed: {}", e.getMessage());
        }
    }

    private String buildExtractionPrompt(String message, AppointmentState.Intent intent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Extract the following information from this user message.\n");
        prompt.append("If a field is not mentioned, return 'null' for that field.\n\n");
        prompt.append("User message: \"").append(message).append("\"\n\n");

        switch (intent) {
            case CREATE_APPOINTMENT:
                prompt.append("Extract: patient_name, doctor_name, date (YYYY-MM-DD), time (HH:MM), reason\n");
                break;
            case CANCEL_APPOINTMENT:
                prompt.append("Extract: appointment_id, patient_name\n");
                break;
            case RESCHEDULE_APPOINTMENT:
                prompt.append("Extract: appointment_id, date (YYYY-MM-DD), time (HH:MM)\n");
                break;
            case LIST_APPOINTMENTS:
                prompt.append("Extract: patient_name\n");
                break;
            default:
                prompt.append("Extract: patient_name, doctor_name, date, time, appointment_id\n");
        }
        prompt.append("\nReturn in format: key=value (one per line)\n");
        return prompt.toString();
    }

    private List<String> identifyMissingFields(AppointmentState state, Map<String, Object> updates) {
        List<String> missing = new ArrayList<>();
        AppointmentState.Intent intent = state.getIntent();

        // Check if value is in state or updates
        boolean hasPatient = (state.getPatientName() != null && !state.getPatientName().isBlank()) ||
                             (updates.get(AppointmentState.PATIENT_NAME) != null && !((String)updates.get(AppointmentState.PATIENT_NAME)).isBlank());
        boolean hasDoctor = (state.getDoctorName() != null && !state.getDoctorName().isBlank()) ||
                            (updates.get(AppointmentState.DOCTOR_NAME) != null && !((String)updates.get(AppointmentState.DOCTOR_NAME)).isBlank());
        boolean hasDate = (state.getAppointmentDate() != null && !state.getAppointmentDate().isBlank()) ||
                          (updates.get(AppointmentState.APPOINTMENT_DATE) != null && !((String)updates.get(AppointmentState.APPOINTMENT_DATE)).isBlank());
        boolean hasTime = (state.getAppointmentTime() != null && !state.getAppointmentTime().isBlank()) ||
                          (updates.get(AppointmentState.APPOINTMENT_TIME) != null && !((String)updates.get(AppointmentState.APPOINTMENT_TIME)).isBlank());
        boolean hasId = state.getAppointmentId() != null || updates.get(AppointmentState.APPOINTMENT_ID) != null;

        switch (intent) {
            case CREATE_APPOINTMENT:
                if (!hasPatient) missing.add("patient name");
                if (!hasDoctor) missing.add("doctor name");
                if (!hasDate) missing.add("appointment date");
                if (!hasTime) missing.add("appointment time");
                // Add reason as the last field to ask (after the essentials)
                boolean hasReason = (state.getReason() != null && !state.getReason().isBlank()) ||
                                    (updates.get(AppointmentState.REASON) != null && !((String)updates.get(AppointmentState.REASON)).isBlank());
                if (!hasReason) missing.add("reason");
                break;
            case CANCEL_APPOINTMENT:
                if (!hasId) missing.add("appointment ID");
                break;
            case RESCHEDULE_APPOINTMENT:
                if (!hasId) missing.add("appointment ID");
                if (!hasDate) missing.add("new date");
                if (!hasTime) missing.add("new time");
                break;
            case LIST_APPOINTMENTS:
                if (!hasPatient) missing.add("patient name");
                break;
        }
        return missing;
    }

    String normalizeDate(String dateStr) {
        if (dateStr == null) return null;
        String lower = dateStr.toLowerCase().trim();
        LocalDate now = LocalDate.now();

        // Relative days
        if (lower.equals("tomorrow")) return now.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (lower.equals("today")) return now.format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (lower.equals("yesterday")) return now.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Next week
        if (lower.equals("next week")) return now.plusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Next <day of week>
        for (int i = 1; i <= 7; i++) {
            String dayName = now.plusDays(i).getDayOfWeek().toString().toLowerCase();
            if (lower.equals("next " + dayName) || lower.equals(dayName)) {
                return now.plusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
        }

        // ISO format: YYYY-MM-DD
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}

        // Month day formats: "Oct 31", "October 31", "Oct. 31", "10/31", "10-31"
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMM.d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("M/d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("M-d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM-dd", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}

        // Fallback: return as-is (caller will handle error)
        return dateStr;
    }

    String normalizeTime(String timeStr) {
        if (timeStr == null) return null;
        timeStr = timeStr.trim();
        try { return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")).format(DateTimeFormatter.ofPattern("HH:mm")); } catch (DateTimeParseException ignored) {}
        try { return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm")).format(DateTimeFormatter.ofPattern("HH:mm")); } catch (DateTimeParseException ignored) {}
        // Handle "10:00 am" / "10:00 pm" / "10 am" / "10 pm" (case insensitive)
        String lower = timeStr.toLowerCase();
        if (lower.contains("am") || lower.contains("pm")) {
            String ampm = lower.contains("pm") ? "pm" : "am";
            // Remove am/pm and parse
            String withoutAmPm = timeStr.replaceAll("(?i)(am|pm)", "").trim();
            try {
                LocalTime t = LocalTime.parse(withoutAmPm, DateTimeFormatter.ofPattern("H:mm"));
                if (ampm.equals("pm") && t.getHour() < 12) t = t.plusHours(12);
                if (ampm.equals("am") && t.getHour() == 12) t = t.minusHours(12);
                return t.format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException ignored) {}
            try {
                LocalTime t = LocalTime.parse(withoutAmPm, DateTimeFormatter.ofPattern("h"));
                if (ampm.equals("pm") && t.getHour() < 12) t = t.plusHours(12);
                if (ampm.equals("am") && t.getHour() == 12) t = t.minusHours(12);
                return t.format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException ignored) {}
        }
        // Fallback: extract just digits and colon
        String digits = timeStr.replaceAll("[^0-9:]", "").trim();
        if (digits.matches("\\d{1,2}:\\d{2}")) {
            try { return LocalTime.parse(digits, DateTimeFormatter.ofPattern("H:mm")).format(DateTimeFormatter.ofPattern("HH:mm")); } catch (DateTimeParseException ignored) {}
        }
        return timeStr;
    }
}
