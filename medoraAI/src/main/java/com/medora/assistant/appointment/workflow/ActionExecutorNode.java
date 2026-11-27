package com.medora.assistant.appointment.workflow;

import com.medora.assistant.appointment.AppointmentEntity;
import com.medora.assistant.appointment.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node 3: Action Executor
 * Executes the actual appointment operation based on intent and extracted data.
 * Returns Map<String, Object> state updates for LangGraph4j.
 */
public class ActionExecutorNode {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutorNode.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a");

    private final AppointmentRepository appointmentRepository;

    public ActionExecutorNode(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * LangGraph4j NodeAction - execute action and return state updates.
     */
    public Map<String, Object> execute(AppointmentState state) {
        log.info("ActionExecutor: Executing action for intent: {}", state.getIntent());

        Map<String, Object> updates = new HashMap<>();
        updates.put(AppointmentState.STEP_COUNT, state.getStepCount() + 1);

        try {
            switch (state.getIntent()) {
                case CREATE_APPOINTMENT:
                    executeCreate(state, updates);
                    break;
                case LIST_APPOINTMENTS:
                    executeList(state, updates);
                    break;
                case CANCEL_APPOINTMENT:
                    executeCancel(state, updates);
                    break;
                case RESCHEDULE_APPOINTMENT:
                    executeReschedule(state, updates);
                    break;
                case UNKNOWN:
                    updates.put(AppointmentState.RESULT,
                        "🤖 I can help you with medical appointments. I can:\n\n" +
                        "• 📅 **Book** a new appointment\n" +
                        "• 📋 **View** your appointments\n" +
                        "• ❌ **Cancel** an appointment\n" +
                        "• 🔄 **Reschedule** an appointment\n\n" +
                        "Just tell me what you need!"
                    );
                    break;
            }
        } catch (Exception e) {
            log.error("ActionExecutor: Error: {}", e.getMessage(), e);
            updates.put(AppointmentState.ERROR, "❌ Error: " + e.getMessage());
        }

        return updates;
    }

    private void executeCreate(AppointmentState state, Map<String, Object> updates) {
        LocalDate date;
        try {
            String normalizedDate = normalizeDate(state.getAppointmentDate());
            date = LocalDate.parse(normalizedDate, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            updates.put(AppointmentState.ERROR, "❌ Could not understand the date '" + state.getAppointmentDate() + "'. Please use a clear date like 'tomorrow', 'Oct 31', or '2024-01-15'.");
            return;
        }

        LocalTime time;
        try {
            String normalizedTime = normalizeTime(state.getAppointmentTime());
            time = LocalTime.parse(normalizedTime, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            updates.put(AppointmentState.ERROR, "❌ Could not understand the time '" + state.getAppointmentTime() + "'. Please use a clear time like '10:00 am' or '14:30'.");
            return;
        }

        if (date.isBefore(LocalDate.now())) {
            updates.put(AppointmentState.ERROR, "❌ The appointment date is in the past. Please choose a future date.");
            return;
        }

        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setPatientName(state.getPatientName().trim());
        appointment.setDoctorName(state.getDoctorName().trim());
        appointment.setAppointmentDate(date);
        appointment.setAppointmentTime(time);
        appointment.setReason(state.getReason() != null ? state.getReason().trim() : "General consultation");
        appointment.setPatientEmail(state.getPatientEmail());
        appointment.setPatientPhone(state.getPatientPhone());

        AppointmentEntity saved = appointmentRepository.save(appointment);
        log.info("ActionExecutor: Appointment created: ID={}", saved.getId());

        String reason = appointment.getReason();
        StringBuilder sb = new StringBuilder();
        sb.append("✅ Appointment booked successfully!\n\n");
        sb.append("📋 Appointment Details:\n");
        sb.append("   • ID: #").append(saved.getId()).append("\n");
        sb.append("   • Patient: ").append(state.getPatientName()).append("\n");
        sb.append("   • Doctor: ").append(state.getDoctorName()).append("\n");
        sb.append("   • Date: ").append(date.format(DISPLAY_DATE)).append("\n");
        sb.append("   • Time: ").append(time.format(DISPLAY_TIME)).append("\n");
        sb.append("   • Reason: ").append(reason).append("\n");
        sb.append("   • Status: Scheduled\n\n");
        sb.append("You can cancel or reschedule anytime by referring to appointment ID #").append(saved.getId()).append(".");
        updates.put(AppointmentState.RESULT, sb.toString());
    }

    private void executeList(AppointmentState state, Map<String, Object> updates) {
        String patientName = state.getPatientName();
        if (patientName == null || patientName.isBlank()) {
            updates.put(AppointmentState.RESULT,
                "❌ Please provide your name to look up your appointments.\n\n" +
                "For example: 'Show my appointments, John Doe'"
            );
            return;
        }

        List<AppointmentEntity> appointments = appointmentRepository.findActiveByPatientName(patientName);
        if (appointments.isEmpty()) {
            updates.put(AppointmentState.RESULT, String.format(
                    "📭 No upcoming appointments found for '%s'.\n\n" +
                    "Would you like to book a new appointment?", patientName));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Upcoming Appointments for '").append(patientName).append("' (").append(appointments.size()).append(" found):\n\n");
        for (AppointmentEntity appt : appointments) {
            sb.append("   #").append(appt.getId())
              .append(" — ").append(appt.getAppointmentDate().format(DISPLAY_DATE))
              .append(" with ").append(appt.getDoctorName()).append("\n")
              .append("      📅 ").append(appt.getAppointmentTime().format(DISPLAY_TIME)).append("\n")
              .append("      📝 ").append(appt.getReason() != null ? appt.getReason() : "General consultation").append("\n")
              .append("      🏷️ Status: ").append(appt.getStatus()).append("\n\n");
        }
        updates.put(AppointmentState.RESULT, sb.toString());
    }

    private void executeCancel(AppointmentState state, Map<String, Object> updates) {
        Long appointmentId = state.getAppointmentId();
        if (appointmentId == null) {
            updates.put(AppointmentState.RESULT,
                "❌ Please provide the appointment ID you want to cancel.\n\n" +
                "For example: 'Cancel appointment #123'"
            );
            return;
        }

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            updates.put(AppointmentState.ERROR, String.format("❌ Appointment #%d not found.", appointmentId));
            return;
        }
        if ("cancelled".equals(appointment.getStatus())) {
            updates.put(AppointmentState.RESULT, String.format("⚠️ Appointment #%d is already cancelled.", appointmentId));
            return;
        }

        appointment.setStatus("cancelled");
        appointmentRepository.save(appointment);
        log.info("ActionExecutor: Appointment cancelled: ID={}", appointmentId);

        StringBuilder sb = new StringBuilder();
        sb.append("✅ Appointment cancelled successfully!\n\n");
        sb.append("📋 Cancelled Appointment:\n");
        sb.append("   • ID: #").append(appointmentId).append("\n");
        sb.append("   • Patient: ").append(appointment.getPatientName()).append("\n");
        sb.append("   • Doctor: ").append(appointment.getDoctorName()).append("\n");
        sb.append("   • Date: ").append(appointment.getAppointmentDate().format(DISPLAY_DATE))
          .append(" at ").append(appointment.getAppointmentTime().format(DISPLAY_TIME));
        updates.put(AppointmentState.RESULT, sb.toString());
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

    private void executeReschedule(AppointmentState state, Map<String, Object> updates) {
        Long appointmentId = state.getAppointmentId();
        if (appointmentId == null) {
            updates.put(AppointmentState.RESULT,
                "❌ Please provide the appointment ID you want to reschedule.");
            return;
        }

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            updates.put(AppointmentState.ERROR, String.format("❌ Appointment #%d not found.", appointmentId));
            return;
        }
        if ("cancelled".equals(appointment.getStatus())) {
            updates.put(AppointmentState.ERROR, String.format("❌ Cannot reschedule — appointment #%d is cancelled.", appointmentId));
            return;
        }

        String newDateStr = state.getNewDate() != null ? state.getNewDate() : state.getAppointmentDate();
        String newTimeStr = state.getNewTime() != null ? state.getNewTime() : state.getAppointmentTime();

        LocalDate newDate;
        try {
            String normalizedDate = normalizeDate(newDateStr);
            newDate = LocalDate.parse(normalizedDate, DATE_FORMAT);
        }
        catch (DateTimeParseException e) {
            updates.put(AppointmentState.ERROR, "❌ Could not understand the date '" + newDateStr + "'. Please use a clear date like 'tomorrow', 'Oct 31', or '2024-01-15'.");
            return;
        }

        LocalTime newTime;
        try {
            String normalizedTime = normalizeTime(newTimeStr);
            newTime = LocalTime.parse(normalizedTime, TIME_FORMAT);
        }
        catch (DateTimeParseException e) {
            updates.put(AppointmentState.ERROR, "❌ Could not understand the time '" + newTimeStr + "'. Please use a clear time like '10:00 am' or '14:30'.");
            return;
        }

        if (newDate.isBefore(LocalDate.now())) {
            updates.put(AppointmentState.ERROR, "❌ The new date is in the past.");
            return;
        }

        String oldDateStr = appointment.getAppointmentDate().format(DISPLAY_DATE);
        String oldTimeStr = appointment.getAppointmentTime().format(DISPLAY_TIME);

        appointment.setAppointmentDate(newDate);
        appointment.setAppointmentTime(newTime);
        appointment.setStatus("rescheduled");
        appointmentRepository.save(appointment);
        log.info("ActionExecutor: Appointment rescheduled: ID={}", appointmentId);

        StringBuilder sb = new StringBuilder();
        sb.append("✅ Appointment rescheduled successfully!\n\n");
        sb.append("📋 Updated Appointment:\n");
        sb.append("   • ID: #").append(appointmentId).append("\n");
        sb.append("   • Patient: ").append(appointment.getPatientName()).append("\n");
        sb.append("   • Doctor: ").append(appointment.getDoctorName()).append("\n");
        sb.append("   • Old Date: ").append(oldDateStr).append(" at ").append(oldTimeStr).append("\n");
        sb.append("   • New Date: ").append(newDate.format(DISPLAY_DATE)).append(" at ").append(newTime.format(DISPLAY_TIME)).append("\n");
        sb.append("   • Status: Rescheduled");
        updates.put(AppointmentState.RESULT, sb.toString());
    }
}
