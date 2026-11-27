package com.medora.kafka.medoraAI.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

	private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
	private final KafkaTemplate<String, String> kafkaTemplate;

	public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	public void send(String message) {
		log.info("Sending message to medora-events: {}", message);
		kafkaTemplate.send("medora-events", message);
	}
}
