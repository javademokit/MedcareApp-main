package com.medora.assistant.appointment.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node 4: Response Formatter
 * Formats the final response based on workflow result.
 * Returns Map<String, Object> state updates for LangGraph4j.
 */
public class ResponseFormatterNode {

    private static final Logger log = LoggerFactory.getLogger(ResponseFormatterNode.class);

    /**
     * LangGraph4j NodeAction - format response and return state updates.
     */
    public Map<String, Object> format(AppointmentState state) {
        log.info("ResponseFormatter: Formatting response for intent: {}", state.getIntent());

        Map<String, Object> updates = new HashMap<>();
        updates.put(AppointmentState.STEP_COUNT, state.getStepCount() + 1);

        // If there's already a result from the executor, keep it
        if (state.getResult() != null && !state.getResult().isBlank()) {
            return updates;
        }

        // If there's an error, use it as result
        if (state.getError() != null && !state.getError().isBlank()) {
            updates.put(AppointmentState.RESULT, state.getError());
            return updates;
        }

        // If data is missing, ask for the missing fields
        if (state.needsFollowUp()) {
            updates.put(AppointmentState.RESULT, buildFollowUpMessage(state));
            return updates;
        }

        // Default fallback
        updates.put(AppointmentState.RESULT,
            "🤖 I'm not sure how to help with that. Could you rephrase your request?\n\n" +
            "I can help you:\n" +
            "• 📅 Book a new appointment\n" +
            "• 📋 View your appointments\n" +
            "• ❌ Cancel an appointment\n" +
            "• 🔄 Reschedule an appointment"
        );

        return updates;
    }

    private String buildFollowUpMessage(AppointmentState state) {
        List<String> missing = state.getMissingFields();
        if (missing.isEmpty()) {
            return "🤖 I'm not sure what information I need. Could you provide more details?";
        }

        // Ask for the FIRST missing field conversationally, one at a time
        String firstMissing = missing.get(0);

        // Build a summary of what we already have
        StringBuilder sb = new StringBuilder();

        switch (state.getIntent()) {
            case CREATE_APPOINTMENT:
                sb.append("😊 Great! I'd love to help you book an appointment.\n\n");
                break;
            case CANCEL_APPOINTMENT:
                sb.append("👍 I can help you cancel an appointment.\n\n");
                break;
            case RESCHEDULE_APPOINTMENT:
                sb.append("🔄 I can help you reschedule.\n\n");
                break;
            case LIST_APPOINTMENTS:
                sb.append("📋 I'll show your appointments.\n\n");
                break;
        }

        // Show what we've collected so far
        boolean hasAny = false;
        if (state.getPatientName() != null && !state.getPatientName().isBlank()) {
            sb.append("   ✓ Your name: ").append(state.getPatientName()).append("\n");
            hasAny = true;
        }
        if (state.getDoctorName() != null && !state.getDoctorName().isBlank()) {
            sb.append("   ✓ Doctor: ").append(state.getDoctorName()).append("\n");
            hasAny = true;
        }
        if (state.getAppointmentDate() != null && !state.getAppointmentDate().isBlank()) {
            sb.append("   ✓ Date: ").append(formatDateNicely(state.getAppointmentDate())).append("\n");
            hasAny = true;
        }
        if (state.getAppointmentTime() != null && !state.getAppointmentTime().isBlank()) {
            sb.append("   ✓ Time: ").append(formatTimeNicely(state.getAppointmentTime())).append("\n");
            hasAny = true;
        }
        if (state.getReason() != null && !state.getReason().isBlank()) {
            sb.append("   ✓ Reason: ").append(state.getReason()).append("\n");
            hasAny = true;
        }

        if (hasAny) {
            sb.append("\n");
        }

        // Ask for the next missing field with a friendly, specific question
        sb.append(getFriendlyQuestion(firstMissing, state.getIntent()));

        return sb.toString();
    }

    private String formatDateNicely(String dateStr) {
        if (dateStr == null) return "";
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            return date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"));
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatTimeNicely(String timeStr) {
        if (timeStr == null) return "";
        try {
            java.time.LocalTime time = java.time.LocalTime.parse(timeStr);
            return time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
        } catch (Exception e) {
            return timeStr;
        }
    }

    private String getFriendlyQuestion(String missingField, AppointmentState.Intent intent) {
        return switch (missingField.toLowerCase()) {
            case "patient name" ->
                "👤 What is your full name?";
            case "doctor name" ->
                "👨‍⚕️ Which doctor would you like to see?";
            case "appointment date" ->
                "📅 What date would you like the appointment?";
            case "appointment time" ->
                "⏰ What time works for you?";
            case "reason" ->
                "📝 What is the reason for your visit?";
            case "appointment id" ->
                "🔢 What is your appointment ID?";
            case "new date" ->
                "📅 What new date would you prefer?";
            case "new time" ->
                "⏰ What new time works for you?";
            default ->
                "Could you please provide the " + missingField + "?";
        };
    }
}
