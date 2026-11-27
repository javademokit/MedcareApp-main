package com.medora.assistant.appointment.workflow;

import org.bsc.langgraph4j.state.AgentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared state for the LangGraph4j appointment workflow.
 * Extends AgentState so it can be used with LangGraph4j's StateGraph.
 * This state flows through all nodes in the graph, carrying data collected at each step.
 */
public class AppointmentState extends AgentState {

    public static final String USER_MESSAGE = "userMessage";
    public static final String INTENT = "intent";
    public static final String PATIENT_NAME = "patientName";
    public static final String DOCTOR_NAME = "doctorName";
    public static final String APPOINTMENT_DATE = "appointmentDate";
    public static final String APPOINTMENT_TIME = "appointmentTime";
    public static final String REASON = "reason";
    public static final String PATIENT_EMAIL = "patientEmail";
    public static final String PATIENT_PHONE = "patientPhone";
    public static final String APPOINTMENT_ID = "appointmentId";
    public static final String NEW_DATE = "newDate";
    public static final String NEW_TIME = "newTime";
    public static final String MISSING_FIELDS = "missingFields";
    public static final String RESULT = "result";
    public static final String ERROR = "error";
    public static final String CONVERSATION_HISTORY = "conversationHistory";
    public static final String STEP_COUNT = "stepCount";
    public static final String NEEDS_FOLLOW_UP = "needsFollowUp";

    /** Intents the workflow can classify */
    public enum Intent {
        CREATE_APPOINTMENT,
        LIST_APPOINTMENTS,
        CANCEL_APPOINTMENT,
        RESCHEDULE_APPOINTMENT,
        UNKNOWN
    }

    public AppointmentState() {
        super(createInitialData());
    }

    /** Constructor that merges input data (e.g. from GraphInput) with initial data */
    public AppointmentState(Map<String, Object> inputData) {
        super(mergeInputData(inputData));
    }

    private static Map<String, Object> createInitialData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put(CONVERSATION_HISTORY, new ArrayList<String>());
        data.put(STEP_COUNT, 0);
        data.put(NEEDS_FOLLOW_UP, false);
        return data;
    }

    private static Map<String, Object> mergeInputData(Map<String, Object> inputData) {
        Map<String, Object> data = createInitialData();
        if (inputData != null) {
            inputData.forEach((key, value) -> {
                if (value != null) {
                    data.put(key, value);
                }
            });
        }
        return data;
    }

    /** Copy constructor for state updates */
    public AppointmentState(AppointmentState other) {
        super(other.data());
    }

    /** Get a value from the state data */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data().get(key);
    }

    /** Put a value in the state data (for internal use during construction) */
    public void put(String key, Object value) {
        // Since data() returns unmodifiable map, we only use this in constructor
        // After construction, nodes return Map<String,Object> updates instead
    }

    public String getUserMessage() {
        return data().get(USER_MESSAGE) != null ? (String) data().get(USER_MESSAGE) : null;
    }

    public void setUserMessage(String msg) {
        // No-op after construction; use Map updates in nodes
    }

    @SuppressWarnings("unchecked")
    public List<String> getConversationHistory() {
        Object obj = data().get(CONVERSATION_HISTORY);
        return obj != null ? (List<String>) obj : new ArrayList<>();
    }

    public void addToHistory(String message) {
        // No-op after construction
    }

    public Intent getIntent() {
        Object obj = data().get(INTENT);
        return obj != null ? (Intent) obj : Intent.UNKNOWN;
    }

    public void setIntent(Intent intent) {
        // No-op after construction; use Map updates in nodes
    }

    public String getPatientName() {
        return data().get(PATIENT_NAME) != null ? (String) data().get(PATIENT_NAME) : null;
    }

    public void setPatientName(String name) {}

    public String getDoctorName() {
        return data().get(DOCTOR_NAME) != null ? (String) data().get(DOCTOR_NAME) : null;
    }

    public void setDoctorName(String name) {}

    public String getAppointmentDate() {
        return data().get(APPOINTMENT_DATE) != null ? (String) data().get(APPOINTMENT_DATE) : null;
    }

    public void setAppointmentDate(String date) {}

    public String getAppointmentTime() {
        return data().get(APPOINTMENT_TIME) != null ? (String) data().get(APPOINTMENT_TIME) : null;
    }

    public void setAppointmentTime(String time) {}

    public String getReason() {
        return data().get(REASON) != null ? (String) data().get(REASON) : null;
    }

    public void setReason(String reason) {}

    public String getPatientEmail() {
        return data().get(PATIENT_EMAIL) != null ? (String) data().get(PATIENT_EMAIL) : null;
    }

    public void setPatientEmail(String email) {}

    public String getPatientPhone() {
        return data().get(PATIENT_PHONE) != null ? (String) data().get(PATIENT_PHONE) : null;
    }

    public void setPatientPhone(String phone) {}

    public Long getAppointmentId() {
        Object obj = data().get(APPOINTMENT_ID);
        return obj != null ? (Long) obj : null;
    }

    public void setAppointmentId(Long id) {}

    public String getNewDate() {
        return data().get(NEW_DATE) != null ? (String) data().get(NEW_DATE) : null;
    }

    public void setNewDate(String date) {}

    public String getNewTime() {
        return data().get(NEW_TIME) != null ? (String) data().get(NEW_TIME) : null;
    }

    public void setNewTime(String time) {}

    @SuppressWarnings("unchecked")
    public List<String> getMissingFields() {
        Object obj = data().get(MISSING_FIELDS);
        return obj != null ? (List<String>) obj : new ArrayList<>();
    }

    public void setMissingFields(List<String> fields) {}

    public String getResult() {
        return data().get(RESULT) != null ? (String) data().get(RESULT) : null;
    }

    public void setResult(String result) {}

    public String getError() {
        return data().get(ERROR) != null ? (String) data().get(ERROR) : null;
    }

    public void setError(String error) {}

    public int getStepCount() {
        Object obj = data().get(STEP_COUNT);
        return obj != null ? (int) obj : 0;
    }

    public void incrementStepCount() {}

    public boolean needsFollowUp() {
        Object obj = data().get(NEEDS_FOLLOW_UP);
        return obj != null && (boolean) obj;
    }

    public void setNeedsFollowUp(boolean needs) {}

    /** Check if all required fields for creating an appointment are present */
    public boolean hasAllCreateFields() {
        return getPatientName() != null && !getPatientName().isBlank() &&
               getDoctorName() != null && !getDoctorName().isBlank() &&
               getAppointmentDate() != null && !getAppointmentDate().isBlank() &&
               getAppointmentTime() != null && !getAppointmentTime().isBlank();
    }

    /** Check if all required fields for canceling are present */
    public boolean hasCancelFields() {
        return getAppointmentId() != null;
    }

    /** Check if all required fields for rescheduling are present */
    public boolean hasRescheduleFields() {
        return getAppointmentId() != null &&
               getNewDate() != null && !getNewDate().isBlank() &&
               getNewTime() != null && !getNewTime().isBlank();
    }
}
