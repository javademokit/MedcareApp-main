package com.medora.kafka.medoraAI.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class DeadLetterQueueConsumer {

	private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueConsumer.class);

	@KafkaListener(topics = "medora-events-dlq", groupId = "medora-dlq-group")
	public void consume(String message) {
		log.error("DLQ - Message failed after all retries: {}", message);
	}
}
