package com.medora.kafka.medoraAI.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	@Bean
	public NewTopic medoraEvents() {
		return TopicBuilder.name("medora-events")
			.partitions(3)
			.replicas(1)
			.build();
	}

	@Bean
	public NewTopic medoraEventsRetry() {
		return TopicBuilder.name("medora-events-retry")
			.partitions(3)
			.replicas(1)
			.build();
	}

	@Bean
	public NewTopic medoraEventsDlq() {
		return TopicBuilder.name("medora-events-dlq")
			.partitions(3)
			.replicas(1)
			.build();
	}
}
