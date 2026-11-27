package com.medora.assistant.appointment;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements the actual appointment tool operations called by the LangChain4j assistant.
 */
@Service
public class AppointmentToolService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentToolService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;

    public AppointmentToolService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Tool("Create a new medical appointment for a patient")
    public String createAppointment(String patientName, String doctorName, String appointmentDate,
                                    String appointmentTime, String reason, String patientEmail, String patientPhone) {
        try {
            // Validate required fields
            if (patientName == null || patientName.isBlank()) {
                return "❌ Patient name is required. Please provide your name.";
            }
            if (doctorName == null || doctorName.isBlank()) {
                return "❌ Doctor name is required. Please specify which doctor you want to see.";
            }
            if (appointmentDate == null || appointmentDate.isBlank()) {
                return "❌ Appointment date is required. Please provide a date (e.g., 2024-01-15).";
            }
            if (appointmentTime == null || appointmentTime.isBlank()) {
                return "❌ Appointment time is required. Please provide a time (e.g., 14:30).";
            }

            // Parse date and time
            LocalDate date;
            LocalTime time;
            try {
                String normalizedDate = normalizeDate(appointmentDate);
                date = LocalDate.parse(normalizedDate, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                return "❌ Could not understand the date '" + appointmentDate + "'. Please use a clear date like 'tomorrow', 'Oct 31', or '2024-01-15'.";
            }

            try {
                String normalizedTime = normalizeTime(appointmentTime);
                time = LocalTime.parse(normalizedTime, TIME_FORMAT);
            } catch (DateTimeParseException e) {
                return "❌ Could not understand the time '" + appointmentTime + "'. Please use a clear time like '10:00 am' or '14:30'.";
            }

            // Check if date is in the past
            if (date.isBefore(LocalDate.now())) {
                return "❌ The appointment date is in the past. Please choose a future date.";
            }

            // Create appointment
            AppointmentEntity appointment = new AppointmentEntity();
            appointment.setPatientName(patientName.trim());
            appointment.setDoctorName(doctorName.trim());
            appointment.setAppointmentDate(date);
            appointment.setAppointmentTime(time);
            appointment.setReason(reason != null ? reason.trim() : "General consultation");
            appointment.setPatientEmail(patientEmail != null ? patientEmail.trim() : null);
            appointment.setPatientPhone(patientPhone != null ? patientPhone.trim() : null);

            AppointmentEntity saved = appointmentRepository.save(appointment);

            log.info("Appointment created: ID={} for {} with {} on {} at {}",
                    saved.getId(), patientName, doctorName, date, time);

            return String.format(
                    "✅ Appointment booked successfully!\n\n" +
                    "📋 Appointment Details:\n" +
                    "   • ID: #%d\n" +
                    "   • Patient: %s\n" +
                    "   • Doctor: %s\n" +
                    "   • Date: %s\n" +
                    "   • Time: %s\n" +
                    "   • Reason: %s\n" +
                    "   • Status: Scheduled\n\n" +
                    "You can cancel or reschedule this appointment anytime by referring to appointment ID #%d.",
                    saved.getId(), patientName, doctorName,
                    date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                    time.format(DateTimeFormatter.ofPattern("h:mm a")),
                    appointment.getReason(), saved.getId()
            );
        } catch (Exception e) {
            log.error("Error creating appointment: {}", e.getMessage(), e);
            return "❌ Failed to create appointment: " + e.getMessage();
        }
    }

    /** Normalize date string to YYYY-MM-DD format, supporting natural language dates */
    private String normalizeDate(String dateStr) {
        if (dateStr == null) return null;
        String lower = dateStr.toLowerCase().trim();
        LocalDate now = LocalDate.now();

        if (lower.equals("tomorrow")) return now.plusDays(1).format(DATE_FORMAT);
        if (lower.equals("today")) return now.format(DATE_FORMAT);
        if (lower.equals("yesterday")) return now.minusDays(1).format(DATE_FORMAT);
        if (lower.equals("next week")) return now.plusWeeks(1).format(DATE_FORMAT);

        for (int i = 1; i <= 7; i++) {
            String dayName = now.plusDays(i).getDayOfWeek().toString().toLowerCase();
            if (lower.equals("next " + dayName) || lower.equals(dayName)) {
                return now.plusDays(i).format(DATE_FORMAT);
            }
        }

        try { return LocalDate.parse(dateStr, DATE_FORMAT).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMM.d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("M/d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("M-d", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM-dd", java.util.Locale.ENGLISH)).withYear(now.getYear()).format(DATE_FORMAT); } catch (DateTimeParseException ignored) {}
        return dateStr;
    }

    /** Normalize time string to HH:mm format, supporting AM/PM */
    private String normalizeTime(String timeStr) {
        if (timeStr == null) return null;
        timeStr = timeStr.trim();
        try { return LocalTime.parse(timeStr, TIME_FORMAT).format(TIME_FORMAT); } catch (DateTimeParseException ignored) {}
        try { return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm")).format(TIME_FORMAT); } catch (DateTimeParseException ignored) {}
        String lower = timeStr.toLowerCase();
        if (lower.contains("am") || lower.contains("pm")) {
            String ampm = lower.contains("pm") ? "pm" : "am";
            String withoutAmPm = timeStr.replaceAll("(?i)(am|pm)", "").trim();
            try {
                LocalTime t = LocalTime.parse(withoutAmPm, DateTimeFormatter.ofPattern("H:mm"));
                if (ampm.equals("pm") && t.getHour() < 12) t = t.plusHours(12);
                if (ampm.equals("am") && t.getHour() == 12) t = t.minusHours(12);
                return t.format(TIME_FORMAT);
            } catch (DateTimeParseException ignored) {}
            try {
                LocalTime t = LocalTime.parse(withoutAmPm, DateTimeFormatter.ofPattern("h"));
                if (ampm.equals("pm") && t.getHour() < 12) t = t.plusHours(12);
                if (ampm.equals("am") && t.getHour() == 12) t = t.minusHours(12);
                return t.format(TIME_FORMAT);
            } catch (DateTimeParseException ignored) {}
        }
        String digits = timeStr.replaceAll("[^0-9:]", "").trim();
        if (digits.matches("\\d{1,2}:\\d{2}")) {
            try { return LocalTime.parse(digits, DateTimeFormatter.ofPattern("H:mm")).format(TIME_FORMAT); } catch (DateTimeParseException ignored) {}
        }
        return timeStr;
    }

    @Tool("List all upcoming appointments for a patient")
    public String listAppointments(String patientName) {
        try {
            if (patientName == null || patientName.isBlank()) {
                return "❌ Please provide your name to look up your appointments.";
            }

            List<AppointmentEntity> appointments = appointmentRepository.findActiveByPatientName(patientName);

            if (appointments.isEmpty()) {
                return String.format(
                        "📭 No upcoming appointments found for '%s'.\n\n" +
                        "Would you like to book a new appointment? Just tell me the doctor's name, date, and time you prefer.",
                        patientName
                );
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("📋 Upcoming Appointments for '%s' (%d found):\n\n", patientName, appointments.size()));

            for (AppointmentEntity appt : appointments) {
                sb.append(String.format(
                        "   #%d — %s with %s\n" +
                        "      📅 %s at %s\n" +
                        "      📝 %s\n" +
                        "      🏷️ Status: %s\n\n",
                        appt.getId(),
                        appt.getAppointmentDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                        appt.getDoctorName(),
                        appt.getAppointmentTime().format(DateTimeFormatter.ofPattern("h:mm a")),
                        appt.getReason() != null ? appt.getReason() : "General consultation",
                        appt.getStatus()
                ));
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Error listing appointments: {}", e.getMessage(), e);
            return "❌ Failed to retrieve appointments: " + e.getMessage();
        }
    }

    @Tool("Cancel a scheduled appointment by ID")
    public String cancelAppointment(Long appointmentId) {
        try {
            if (appointmentId == null) {
                return "❌ Please provide the appointment ID you want to cancel.";
            }

            AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if ("cancelled".equals(appointment.getStatus())) {
                return String.format("⚠️ Appointment #%d is already cancelled.", appointmentId);
            }

            appointment.setStatus("cancelled");
            appointmentRepository.save(appointment);

            log.info("Appointment cancelled: ID={} for {}", appointmentId, appointment.getPatientName());

            return String.format(
                    "✅ Appointment cancelled successfully!\n\n" +
                    "📋 Cancelled Appointment:\n" +
                    "   • ID: #%d\n" +
                    "   • Patient: %s\n" +
                    "   • Doctor: %s\n" +
                    "   • Date: %s at %s\n\n" +
                    "If you change your mind, you can book a new appointment anytime.",
                    appointmentId, appointment.getPatientName(), appointment.getDoctorName(),
                    appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                    appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("h:mm a"))
            );
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return String.format("❌ Appointment #%d not found. Please check the ID and try again.", appointmentId);
            }
            log.error("Error cancelling appointment: {}", e.getMessage(), e);
            return "❌ Failed to cancel appointment: " + e.getMessage();
        } catch (Exception e) {
            log.error("Error cancelling appointment: {}", e.getMessage(), e);
            return "❌ Failed to cancel appointment: " + e.getMessage();
        }
    }

    @Tool("Reschedule an existing appointment to a new date and time")
    public String rescheduleAppointment(Long appointmentId, String newDate, String newTime) {
        try {
            if (appointmentId == null) {
                return "❌ Please provide the appointment ID you want to reschedule.";
            }
            if (newDate == null || newDate.isBlank()) {
                return "❌ Please provide the new date (YYYY-MM-DD format).";
            }
            if (newTime == null || newTime.isBlank()) {
                return "❌ Please provide the new time (HH:MM format).";
            }

            AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if ("cancelled".equals(appointment.getStatus())) {
                return String.format("❌ Cannot reschedule appointment #%d — it has been cancelled.", appointmentId);
            }

            // Parse new date and time
            LocalDate date;
            LocalTime time;
            try {
                String normalizedDate = normalizeDate(newDate);
                date = LocalDate.parse(normalizedDate, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                return "❌ Could not understand the date '" + newDate + "'. Please use a clear date like 'tomorrow', 'Oct 31', or '2024-01-15'.";
            }

            try {
                String normalizedTime = normalizeTime(newTime);
                time = LocalTime.parse(normalizedTime, TIME_FORMAT);
            } catch (DateTimeParseException e) {
                return "❌ Could not understand the time '" + newTime + "'. Please use a clear time like '10:00 am' or '14:30'.";
            }

            if (date.isBefore(LocalDate.now())) {
                return "❌ The new date is in the past. Please choose a future date.";
            }

            // Store old details for the response
            String oldDateStr = appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"));
            String oldTimeStr = appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("h:mm a"));

            // Update appointment
            appointment.setAppointmentDate(date);
            appointment.setAppointmentTime(time);
            appointment.setStatus("rescheduled");
            appointmentRepository.save(appointment);

            log.info("Appointment rescheduled: ID={} from {} to {}", appointmentId, oldDateStr, date);

            return String.format(
                    "✅ Appointment rescheduled successfully!\n\n" +
                    "📋 Updated Appointment:\n" +
                    "   • ID: #%d\n" +
                    "   • Patient: %s\n" +
                    "   • Doctor: %s\n" +
                    "   • Old Date: %s at %s\n" +
                    "   • New Date: %s at %s\n" +
                    "   • Status: Rescheduled\n\n" +
                    "Your appointment has been updated. Is there anything else I can help with?",
                    appointmentId, appointment.getPatientName(), appointment.getDoctorName(),
                    oldDateStr, oldTimeStr,
                    date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                    time.format(DateTimeFormatter.ofPattern("h:mm a"))
            );
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return String.format("❌ Appointment #%d not found. Please check the ID and try again.", appointmentId);
            }
            log.error("Error rescheduling appointment: {}", e.getMessage(), e);
            return "❌ Failed to reschedule appointment: " + e.getMessage();
        } catch (Exception e) {
            log.error("Error rescheduling appointment: {}", e.getMessage(), e);
            return "❌ Failed to reschedule appointment: " + e.getMessage();
        }
    }
}
