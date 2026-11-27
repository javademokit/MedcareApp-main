package com.medora.assistant.appointment.workflow;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * LangGraph4j workflow for managing medical appointments.
 * 
 * Workflow graph structure:
 * 
 *   START → classify_intent → extract_data → [conditional]
 *                                    ↓ needs_follow_up?
 *                              yes → format_response → END
 *                              no  → execute_action → format_response → END
 * 
 * Uses LangGraph4j's StateGraph to manage state flow between nodes,
 * with conditional edges for handling missing data and follow-up questions.
 */
public class AppointmentWorkflow {

    private static final Logger log = LoggerFactory.getLogger(AppointmentWorkflow.class);

    private final CompiledGraph<AppointmentState> compiledGraph;
    private final IntentClassifierNode intentClassifier;
    private final DataExtractorNode dataExtractor;
    private final ActionExecutorNode actionExecutor;
    private final ResponseFormatterNode responseFormatter;
    
    // Shared trace list for capturing node execution steps (thread-safe)
    private volatile List<Map<String, Object>> executionTrace = new CopyOnWriteArrayList<>();

    public AppointmentWorkflow(
            ChatLanguageModel chatModel,
            com.medora.assistant.appointment.AppointmentRepository appointmentRepository) {
        
        // Create all nodes
        this.intentClassifier = new IntentClassifierNode(chatModel);
        this.dataExtractor = new DataExtractorNode(chatModel);
        this.actionExecutor = new ActionExecutorNode(appointmentRepository);
        this.responseFormatter = new ResponseFormatterNode();

        // Build the state graph
        this.compiledGraph = buildGraph();
        
        log.info("AppointmentWorkflow: LangGraph4j workflow initialized and compiled");
    }

    /**
     * Build and compile the LangGraph4j StateGraph.
     * 
     * Workflow structure:
     *   START → classify_intent → extract_data → [conditional]
     *                                            ↓
     *                                     needs_follow_up?
     *                                    /            \
     *                                  yes              no
     *                                  /                  \
     *                          format_response        execute_action
     *                                  \                  /
     *                                   \              /
     *                              format_response (if not already)
     *                                        |
     *                                       END
     */
    private CompiledGraph<AppointmentState> buildGraph() {
        log.info("AppointmentWorkflow: Building state graph");

        try {
            // Create graph with AppointmentState factory that merges input data
            AgentStateFactory<AppointmentState> factory = (Map<String, Object> initData) -> {
                return new AppointmentState(initData);
            };
            StateGraph<AppointmentState> graph = new StateGraph<>(factory);

            // Add nodes to the graph with tracing wrappers
            graph.addNode("classify_intent", (state, config) -> {
                Map<String, Object> result = intentClassifier.classify(state);
                traceNode("classify_intent", "Intent Classifier", state, result, "Classified user intent");
                return completedFuture(result);
            });
            graph.addNode("extract_data", (state, config) -> {
                Map<String, Object> result = dataExtractor.extract(state);
                traceNode("extract_data", "Data Extractor", state, result, "Extracted structured data");
                return completedFuture(result);
            });
            graph.addNode("execute_action", (state, config) -> {
                Map<String, Object> result = actionExecutor.execute(state);
                traceNode("execute_action", "Action Executor", state, result, "Executed appointment action");
                return completedFuture(result);
            });
            graph.addNode("format_response", (state, config) -> {
                Map<String, Object> result = responseFormatter.format(state);
                traceNode("format_response", "Response Formatter", state, result, "Formatted final response");
                return completedFuture(result);
            });

            // Define edges:
            // START → classify_intent
            graph.addEdge(GraphDefinition.START, "classify_intent");

            // classify_intent → extract_data (always)
            graph.addEdge("classify_intent", "extract_data");

            // extract_data → conditional edge based on whether data is complete
            // If data is missing, go directly to format_response to ask follow-up questions
            // If data is complete, go to execute_action to perform the operation
            graph.addConditionalEdges("extract_data",
                (state, config) -> {
                    String nextNode;
                    if (state.needsFollowUp()) {
                        nextNode = "format_response";
                    } else {
                        nextNode = "execute_action";
                    }
                    log.info("AppointmentWorkflow: Conditional edge from extract_data → {}", nextNode);
                    return completedFuture(new Command(nextNode));
                },
                Map.of(
                    "format_response", "format_response",
                    "execute_action", "execute_action"
                )
            );

            // execute_action → format_response
            graph.addEdge("execute_action", "format_response");

            // format_response → END
            graph.addEdge("format_response", GraphDefinition.END);

            // Compile the graph
            CompiledGraph<AppointmentState> compiled = graph.compile();
            log.info("AppointmentWorkflow: Graph compiled successfully");
            return compiled;
        } catch (Exception e) {
            log.error("AppointmentWorkflow: Failed to build graph: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build appointment workflow graph", e);
        }
    }

    /**
     * Process a user message through the workflow.
     * Returns the final formatted response.
     */
    public String process(String userMessage) {
        return process(userMessage, new java.util.HashMap<>(), false);
    }
    
    /**
     * Get the execution trace from the last workflow run.
     * Returns a list of node execution steps with state snapshots.
     */
    public List<Map<String, Object>> getLastExecutionTrace() {
        return Collections.unmodifiableList(new ArrayList<>(executionTrace));
    }

    /**
     * Process a user message through the workflow, merging previously collected session state.
     * @param userMessage the current user message
     * @param sessionState previously collected data from earlier turns (patientName, doctorName, etc.)
     * @return the final formatted response
     */
    public String process(String userMessage, Map<String, String> sessionState) {
        return process(userMessage, sessionState, false);
    }
    
    /**
     * Process a user message through the workflow with optional tracing.
     * @param userMessage the current user message
     * @param sessionState previously collected data from earlier turns
     * @param trace whether to capture execution trace
     * @return the final formatted response
     */
    public String process(String userMessage, Map<String, String> sessionState, boolean trace) {
        log.info("AppointmentWorkflow: Processing message: {} with session state: {}", userMessage, sessionState);

        // Build the input map: start with session state, then add the user message
        Map<String, Object> input = new java.util.HashMap<>();
        boolean hasSessionData = false;
        AppointmentState.Intent savedIntent = null;
        if (sessionState != null) {
            for (Map.Entry<String, String> entry : sessionState.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    String key = entry.getKey().toLowerCase();
                    if (key.equals("intent")) {
                        // Restore the saved intent
                        try {
                            savedIntent = AppointmentState.Intent.valueOf(entry.getValue());
                        } catch (IllegalArgumentException e) {
                            // ignore invalid intent
                        }
                    } else {
                        // Map session state keys to AppointmentState keys
                        String stateKey = mapSessionKeyToStateKey(entry.getKey());
                        input.put(stateKey, entry.getValue());
                        hasSessionData = true;
                    }
                }
            }
        }
        input.put(AppointmentState.USER_MESSAGE, userMessage);

        // If session already has an intent, restore it so follow-up messages work
        if (savedIntent != null) {
            input.put(AppointmentState.INTENT, savedIntent);
        } else if (hasSessionData) {
            // Fallback: if we have partial data but no saved intent, assume CREATE
            input.put(AppointmentState.INTENT, AppointmentState.Intent.CREATE_APPOINTMENT);
        }

        try {
            // Clear previous trace if tracing is enabled
            if (trace) {
                executionTrace = new CopyOnWriteArrayList<>();
                executionTrace.add(Map.of(
                    "step", 0,
                    "node", "START",
                    "nodeName", "Workflow Start",
                    "description", "User message entered the workflow",
                    "inputMessage", userMessage,
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Run the graph with merged state
            Optional<AppointmentState> result = compiledGraph.invoke(
                GraphInput.args(input),
                RunnableConfig.empty()
            );

            if (result.isPresent()) {
                AppointmentState finalState = result.get();
                String response = finalState.getResult();
                
                // Save any newly extracted data back to session state for the next turn
                if (sessionState != null) {
                    // Always save the intent so follow-up messages know the context
                    if (finalState.getIntent() != AppointmentState.Intent.UNKNOWN) {
                        sessionState.put("intent", finalState.getIntent().name());
                    }
                    if (finalState.getPatientName() != null && !finalState.getPatientName().isBlank()) {
                        sessionState.put("patientName", finalState.getPatientName());
                    }
                    if (finalState.getDoctorName() != null && !finalState.getDoctorName().isBlank()) {
                        sessionState.put("doctorName", finalState.getDoctorName());
                    }
                    if (finalState.getAppointmentDate() != null && !finalState.getAppointmentDate().isBlank()) {
                        sessionState.put("appointmentDate", finalState.getAppointmentDate());
                    }
                    if (finalState.getAppointmentTime() != null && !finalState.getAppointmentTime().isBlank()) {
                        sessionState.put("appointmentTime", finalState.getAppointmentTime());
                    }
                    if (finalState.getReason() != null && !finalState.getReason().isBlank()) {
                        sessionState.put("reason", finalState.getReason());
                    }
                    if (finalState.getPatientEmail() != null && !finalState.getPatientEmail().isBlank()) {
                        sessionState.put("patientEmail", finalState.getPatientEmail());
                    }
                    if (finalState.getPatientPhone() != null && !finalState.getPatientPhone().isBlank()) {
                        sessionState.put("patientPhone", finalState.getPatientPhone());
                    }
                }
                
                log.info("AppointmentWorkflow: Completed in {} steps, session state: {}", finalState.getStepCount(), sessionState);
                
                // Add END step to trace
                if (trace) {
                    executionTrace.add(Map.of(
                        "step", executionTrace.size(),
                        "node", "END",
                        "nodeName", "Workflow End",
                        "description", "Workflow completed successfully",
                        "totalSteps", finalState.getStepCount(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
                
                if (response != null) {
                    return response;
                } else if (finalState.getError() != null) {
                    return finalState.getError();
                } else {
                    return "🤖 I'm not sure how to help with that. Could you rephrase your request?";
                }
            } else {
                log.warn("AppointmentWorkflow: Graph returned no result");
                return "❌ Sorry, I couldn't process your request. Please try again.";
            }
        } catch (Exception e) {
            log.error("AppointmentWorkflow: Error processing message: {}", e.getMessage(), e);
            return "❌ Sorry, an error occurred while processing your request. Please try again.";
        }
    }

    /**
     * Map session state keys (simple names) to AppointmentState constant keys.
     */
    private String mapSessionKeyToStateKey(String key) {
        if (key == null) return key;
        return switch (key.toLowerCase()) {
            case "patientname" -> AppointmentState.PATIENT_NAME;
            case "doctorname" -> AppointmentState.DOCTOR_NAME;
            case "appointmentdate" -> AppointmentState.APPOINTMENT_DATE;
            case "appointmenttime" -> AppointmentState.APPOINTMENT_TIME;
            case "reason" -> AppointmentState.REASON;
            case "patientemail" -> AppointmentState.PATIENT_EMAIL;
            case "patientphone" -> AppointmentState.PATIENT_PHONE;
            default -> key;
        };
    }

    /**
     * Get the workflow graph visualization in the specified format.
     * Supports Mermaid and PlantUML diagram formats.
     *
     * @param type the diagram type (MERMAID or PLANTUML)
     * @return the graph representation with diagram code
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type) {
        return compiledGraph.getGraph(type);
    }
    
    /**
     * Trace a node execution step for visualization.
     */
    private void traceNode(String nodeId, String nodeName, AppointmentState stateBefore, 
                          Map<String, Object> result, String description) {
        List<Map<String, Object>> trace = executionTrace;
        
        // Build state snapshot
        Map<String, Object> stateSnapshot = new java.util.HashMap<>();
        stateSnapshot.put("intent", stateBefore.getIntent().name());
        if (stateBefore.getPatientName() != null) stateSnapshot.put("patientName", stateBefore.getPatientName());
        if (stateBefore.getDoctorName() != null) stateSnapshot.put("doctorName", stateBefore.getDoctorName());
        if (stateBefore.getAppointmentDate() != null) stateSnapshot.put("appointmentDate", stateBefore.getAppointmentDate());
        if (stateBefore.getAppointmentTime() != null) stateSnapshot.put("appointmentTime", stateBefore.getAppointmentTime());
        if (stateBefore.getReason() != null) stateSnapshot.put("reason", stateBefore.getReason());
        if (stateBefore.getResult() != null) stateSnapshot.put("result", stateBefore.getResult());
        
        // Build result summary
        Map<String, Object> resultSummary = new java.util.HashMap<>();
        result.forEach((key, value) -> {
            if (key != null && !key.equals(AppointmentState.STEP_COUNT)) {
                resultSummary.put(key, value);
            }
        });
        
        // Determine the next node based on the result
        String nextNode = null;
        if ("extract_data".equals(nodeId)) {
            if (stateBefore.needsFollowUp()) {
                nextNode = "format_response";
            } else {
                nextNode = "execute_action";
            }
        } else if ("classify_intent".equals(nodeId)) {
            nextNode = "extract_data";
        } else if ("execute_action".equals(nodeId)) {
            nextNode = "format_response";
        } else if ("format_response".equals(nodeId)) {
            nextNode = "END";
        }
        
        trace.add(Map.of(
            "step", trace.size(),
            "node", nodeId,
            "nodeName", nodeName,
            "description", description,
            "stateBefore", stateSnapshot,
            "result", resultSummary,
            "nextNode", nextNode,
            "timestamp", System.currentTimeMillis()
        ));
        
        log.info("AppointmentWorkflow: Traced node {} → {}", nodeId, nextNode);
    }
}
