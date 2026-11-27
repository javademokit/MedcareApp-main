package com.medora.kafka.medoraAI.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class DoctorConsumer {

	private static final Logger log = LoggerFactory.getLogger(DoctorConsumer.class);

	@KafkaListener(topics = "medora-events", groupId = "medora-doctor-group")
	public void consume(String message) {
		log.info("Doctor consumer received: {}", message);
	}
}
