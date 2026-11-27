package com.medora.kafka.medoraAI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.ai.autoconfigure.chat.client.ChatClientAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
		OllamaAutoConfiguration.class,
		PgVectorStoreAutoConfiguration.class,
		ChatClientAutoConfiguration.class
})
@ComponentScan(basePackages = {"com.medora.kafka.medoraAI", "com.medora.assistant"})
@EnableAsync
@EntityScan(basePackages = {"com.medora.assistant"})
@EnableJpaRepositories(basePackages = {"com.medora.assistant"})
public class MedoraAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MedoraAiApplication.class, args);
	}
}
