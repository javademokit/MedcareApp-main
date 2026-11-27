package com.medora.kafka.medoraAI.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

	private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

	@KafkaListener(topics = "medora-events", groupId = "medora-default-group")
	public void consume(String message) {
		log.info("Received message: {}", message);
	}
}
