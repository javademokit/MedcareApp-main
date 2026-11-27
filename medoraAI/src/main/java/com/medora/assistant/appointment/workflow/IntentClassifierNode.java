package com.medora.assistant.appointment.workflow;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;

/**
 * Node 1: Intent Classifier
 * Determines what the user wants to do: create, list, cancel, or reschedule an appointment.
 * Uses rule-based classification first, then LLM fallback for ambiguous messages.
 * 
 * LangGraph4j node - returns Map<String, Object> state updates.
 */
public class IntentClassifierNode {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifierNode.class);
    private final ChatLanguageModel chatModel;

    public IntentClassifierNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * LangGraph4j NodeAction - classify intent and return state updates.
     * If intent is already set (from session state carry-over), keeps it unless
     * the user explicitly asks for something different (cancel, list, reschedule).
     */
    public Map<String, Object> classify(AppointmentState state) {
        log.info("IntentClassifier: Processing message: {}", state.getUserMessage());

        String message = state.getUserMessage();
        if (message == null || message.isBlank()) {
            return Map.of(AppointmentState.ERROR, "No message provided", AppointmentState.STEP_COUNT, state.getStepCount() + 1);
        }

        // If intent is already set from session state, check if user is switching intent
        AppointmentState.Intent existingIntent = state.getIntent();
        if (existingIntent != AppointmentState.Intent.UNKNOWN) {
            // Check if the user is explicitly asking for a different action
            AppointmentState.Intent newIntent = classifyByRules(message);
            // Only override if the user clearly wants something different
            if (newIntent != AppointmentState.Intent.UNKNOWN &&
                (newIntent == AppointmentState.Intent.CANCEL_APPOINTMENT ||
                 newIntent == AppointmentState.Intent.LIST_APPOINTMENTS ||
                 newIntent == AppointmentState.Intent.RESCHEDULE_APPOINTMENT)) {
                log.info("IntentClassifier: User switched from {} to {}", existingIntent, newIntent);
                return Map.of(
                    AppointmentState.INTENT, newIntent,
                    AppointmentState.STEP_COUNT, state.getStepCount() + 1
                );
            }
            // Keep the existing intent — user is likely answering a follow-up question
            log.info("IntentClassifier: Keeping existing intent {} (user answering follow-up)", existingIntent);
            return Map.of(
                AppointmentState.INTENT, existingIntent,
                AppointmentState.STEP_COUNT, state.getStepCount() + 1
            );
        }

        // Try rule-based classification first
        AppointmentState.Intent intent = classifyByRules(message);

        // If rule-based classification is uncertain, use LLM
        if (intent == AppointmentState.Intent.UNKNOWN) {
            intent = classifyByLLM(message);
        }

        log.info("IntentClassifier: Classified as {}", intent);
        return Map.of(
            AppointmentState.INTENT, intent,
            AppointmentState.STEP_COUNT, state.getStepCount() + 1
        );
    }

    /**
     * Rule-based intent classification using keyword matching.
     */
    private AppointmentState.Intent classifyByRules(String message) {
        String lower = message.toLowerCase().trim();

        // Cancel patterns
        if (lower.contains("cancel") || lower.contains("delete") || lower.contains("remove") ||
            lower.contains("call off")) {
            return AppointmentState.Intent.CANCEL_APPOINTMENT;
        }

        // Reschedule patterns
        if (lower.contains("reschedule") || lower.contains("change") ||
            lower.contains("move") || lower.contains("postpone") || lower.contains("different time") ||
            lower.contains("different date")) {
            return AppointmentState.Intent.RESCHEDULE_APPOINTMENT;
        }

        // List patterns (avoid matching "checkup" as "check")
        if (lower.contains("my appointment") || lower.contains("my appointments") ||
            lower.contains("list") || lower.contains("show") || lower.contains("view") ||
            lower.contains("check my") || lower.contains("check your") || lower.contains("check my appointments") ||
            lower.contains("upcoming") || lower.contains("scheduled") ||
            lower.contains("what do i have")) {
            return AppointmentState.Intent.LIST_APPOINTMENTS;
        }

        // Create patterns
        if (lower.contains("book") || lower.contains("schedule") || lower.contains("appointment") ||
            lower.contains("see doctor") || lower.contains("see a") || lower.contains("make an") ||
            lower.contains("set up") || lower.contains("create") || lower.contains("i want") ||
            lower.contains("i need") || lower.contains("i'd like") || lower.contains("dr.")) {
            return AppointmentState.Intent.CREATE_APPOINTMENT;
        }

        return AppointmentState.Intent.UNKNOWN;
    }

    /**
     * LLM-based intent classification for ambiguous messages.
     */
    private AppointmentState.Intent classifyByLLM(String message) {
        try {
            String prompt = String.format(
                "Classify the following user message into one of these intents: CREATE_APPOINTMENT, LIST_APPOINTMENTS, CANCEL_APPOINTMENT, RESCHEDULE_APPOINTMENT, UNKNOWN.\n\n" +
                "User message: \"%s\"\n\n" +
                "Return ONLY the intent name, nothing else.",
                message
            );

            String response = chatModel.generate(Arrays.asList(new UserMessage(prompt))).content().text().trim();
            log.info("IntentClassifier LLM response: {}", response);

            if (response.contains("CREATE")) return AppointmentState.Intent.CREATE_APPOINTMENT;
            if (response.contains("LIST")) return AppointmentState.Intent.LIST_APPOINTMENTS;
            if (response.contains("CANCEL")) return AppointmentState.Intent.CANCEL_APPOINTMENT;
            if (response.contains("RESCHEDULE")) return AppointmentState.Intent.RESCHEDULE_APPOINTMENT;

            return AppointmentState.Intent.UNKNOWN;
        } catch (Exception e) {
            log.error("IntentClassifier: LLM classification failed: {}", e.getMessage());
            return AppointmentState.Intent.UNKNOWN;
        }
    }
}
