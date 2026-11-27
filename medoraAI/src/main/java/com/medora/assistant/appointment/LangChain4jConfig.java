package com.medora.assistant.appointment;

import com.medora.assistant.appointment.workflow.AppointmentWorkflow;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * Configures LangChain4j with Ollama for the appointment chat bot.
 * Also configures LangGraph4j workflow for structured appointment management.
 * Uses lazy initialization so the app starts even if Ollama is not running yet.
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model:llama3.2}")
    private String modelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing LangChain4j Ollama chat model: {} at {}", modelName, ollamaBaseUrl);
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(modelName)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * LangChain4j AI Services assistant (tool-calling approach).
     */
    @Bean
    public AppointmentAssistant appointmentAssistant(ChatLanguageModel chatModel, AppointmentToolService toolService) {
        log.info("Initializing AppointmentAssistant with LangChain4j");
        return AiServices.builder(AppointmentAssistant.class)
                .chatLanguageModel(chatModel)
                .tools(toolService)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .build())
                .build();
    }

    /**
     * LangGraph4j workflow-based appointment assistant.
     * Uses a state graph with nodes for intent classification, data extraction,
     * action execution, and response formatting.
     */
    @Bean
    public AppointmentWorkflow appointmentWorkflow(ChatLanguageModel chatModel, AppointmentRepository repository) {
        log.info("Initializing AppointmentWorkflow with LangGraph4j");
        return new AppointmentWorkflow(chatModel, repository);
    }
}
