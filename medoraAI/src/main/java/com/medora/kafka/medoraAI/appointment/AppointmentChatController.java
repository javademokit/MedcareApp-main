package com.medora.kafka.medoraAI.appointment;

import com.medora.assistant.appointment.AppointmentAssistant;
import com.medora.assistant.appointment.AppointmentRepository;
import com.medora.assistant.appointment.workflow.AppointmentWorkflow;
import org.bsc.langgraph4j.GraphRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the appointment chat bot.
 * Supports two modes:
 * 1. LangGraph4j workflow (default) - structured state graph with intent classification,
 *    data extraction, action execution, and response formatting.
 * 2. LangChain4j tool-calling (fallback) - direct AI service with tool-calling.
 * 
 * Patients can book, list, cancel, and reschedule appointments through natural conversation.
 */
@RestController
@RequestMapping("/api/appointment-chat")
public class AppointmentChatController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentChatController.class);

    @Autowired(required = false)
    private AppointmentWorkflow appointmentWorkflow;

    @Autowired(required = false)
    private AppointmentAssistant appointmentAssistant;
    
    private final AppointmentRepository appointmentRepository;

    public AppointmentChatController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Chat with the appointment bot using LangGraph4j workflow.
     * POST /api/appointment-chat/message
     * Body: { "sessionId": "optional-uuid", "message": "Book an appointment with Dr. Smith for tomorrow at 10am" }
     */
    // In-memory session state store: sessionId -> collected data
    private final java.util.Map<String, Map<String, String>> sessionStore = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String message = request.get("message");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Please provide a message"
            ));
        }

        log.info("Chat request [session={}]: {}", sessionId, message);

        try {
            // Try LangGraph4j workflow with session state (preferred approach)
            if (appointmentWorkflow != null) {
                // Retrieve existing session state
                Map<String, String> sessionState = sessionStore.computeIfAbsent(sessionId, k -> new java.util.HashMap<>());
                
                String response = appointmentWorkflow.process(message, sessionState);
                
                // Check if the appointment is complete — clear session state
                if (response.contains("Appointment booked successfully") || 
                    response.contains("Appointment cancelled") ||
                    response.contains("Appointment rescheduled") ||
                    response.contains("Upcoming Appointments")) {
                    sessionStore.remove(sessionId);
                }
                
                log.info("LangGraph4j workflow response [session={}]: {} chars", sessionId, response.length());
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "sessionId", sessionId,
                        "response", response,
                        "engine", "langgraph4j"
                ));
            }

            // Fallback to LangChain4j tool-calling
            if (appointmentAssistant != null) {
                String response = appointmentAssistant.chat(message);
                log.info("LangChain4j response [session={}]: {} chars", sessionId, response.length());
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "sessionId", sessionId,
                        "response", response,
                        "engine", "langchain4j"
                ));
            }

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "error",
                    "message", "Chat bot is unavailable. Please ensure Ollama is running (ollama serve) and has the llama3.2 model pulled."
            ));
        } catch (Exception e) {
            log.error("Error in chat: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to process your request: " + e.getMessage()
            ));
        }
    }

    /**
     * Get all appointments (admin view).
     * GET /api/appointment-chat/appointments
     */
    @GetMapping("/appointments")
    public ResponseEntity<?> getAppointments() {
        var appointments = appointmentRepository.findAllScheduled();
        return ResponseEntity.ok(appointments);
    }

    /**
     * Get appointments by patient name.
     * GET /api/appointment-chat/appointments?patientName=John
     */
    @GetMapping("/appointments/patient")
    public ResponseEntity<?> getPatientAppointments(@RequestParam String patientName) {
        var appointments = appointmentRepository.findActiveByPatientName(patientName);
        return ResponseEntity.ok(appointments);
    }

    /**
     * Get the LangGraph4j workflow graph as a Mermaid diagram.
     * GET /api/appointment-chat/graph
     * Returns the visual representation of the state graph with all nodes and edges.
     */
    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> getWorkflowGraph() {
        if (appointmentWorkflow == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "error",
                    "message", "Workflow not available. Please ensure Ollama is running."
            ));
        }

        try {
            GraphRepresentation graph = appointmentWorkflow.getGraph(GraphRepresentation.Type.MERMAID);
            // Strip YAML frontmatter that LangGraph4j adds — Mermaid.js cannot parse it
            String content = graph.content();
            if (content.startsWith("---")) {
                int endIdx = content.indexOf("---", 3);
                if (endIdx != -1) {
                    content = content.substring(endIdx + 3).trim();
                }
            }
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "type", graph.type().name(),
                    "content", content
            ));
        } catch (Exception e) {
            log.error("Failed to generate workflow graph: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to generate graph: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Simulate the workflow with step-by-step execution trace.
     * POST /api/appointment-chat/simulate
     * Body: { "message": "Book an appointment for John Doe with Dr. Smith for tomorrow at 10am" }
     * Returns the response plus a detailed trace of each node execution.
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Please provide a message"
            ));
        }

        if (appointmentWorkflow == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "error",
                    "message", "Workflow not available. Please ensure Ollama is running."
            ));
        }

        log.info("Simulation request: {}", message);

        try {
            // Process with tracing enabled
            String response = appointmentWorkflow.process(message, new java.util.HashMap<>(), true);
            
            // Get the execution trace
            List<Map<String, Object>> trace = appointmentWorkflow.getLastExecutionTrace();
            
            // Build workflow info
            List<Map<String, String>> nodes = List.of(
                Map.of("id", "START", "name", "Workflow Start", "color", "#10b981", "icon", "▶️"),
                Map.of("id", "classify_intent", "name", "Intent Classifier", "color", "#3b82f6", "icon", "🔍"),
                Map.of("id", "extract_data", "name", "Data Extractor", "color", "#f59e0b", "icon", "📝"),
                Map.of("id", "execute_action", "name", "Action Executor", "color", "#ef4444", "icon", "⚡"),
                Map.of("id", "format_response", "name", "Response Formatter", "color", "#06b6d4", "icon", "💬"),
                Map.of("id", "END", "name", "Workflow End", "color", "#6366f1", "icon", "🏁")
            );
            
            List<Map<String, String>> edges = List.of(
                Map.of("from", "START", "to", "classify_intent", "type", "solid"),
                Map.of("from", "classify_intent", "to", "extract_data", "type", "solid"),
                Map.of("from", "extract_data", "to", "execute_action", "type", "conditional", "label", "data complete"),
                Map.of("from", "extract_data", "to", "format_response", "type", "conditional", "label", "needs follow-up"),
                Map.of("from", "execute_action", "to", "format_response", "type", "solid"),
                Map.of("from", "format_response", "to", "END", "type", "solid")
            );
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "response", response,
                    "trace", trace,
                    "nodes", nodes,
                    "edges", edges
            ));
        } catch (Exception e) {
            log.error("Error in simulation: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to simulate: " + e.getMessage()
            ));
        }
    }
}
