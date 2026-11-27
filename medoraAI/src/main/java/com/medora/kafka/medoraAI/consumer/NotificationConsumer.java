package com.medora.kafka.medoraAI.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.kafka.medoraAI.service.EmailService;
import com.medora.kafka.medoraAI.service.SMSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumer {

	private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
	private final EmailService emailService;
	private final SMSService smsService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public NotificationConsumer(EmailService emailService, SMSService smsService) {
		this.emailService = emailService;
		this.smsService = smsService;
	}

	@KafkaListener(topics = "medora-events", groupId = "medora-notification-group")
	public void consume(String message) {
		try {
			JsonNode node = objectMapper.readTree(message);
			String eventType = node.path("eventType").asText();

			if ("doctor_assessment_completed".equals(eventType)) {
				String patientName = node.path("patientName").asText("");
				String patientEmail = node.path("patientEmail").asText("");
				String patientPhone = node.path("patientPhone").asText("");
				String doctorName = node.path("doctorName").asText("");
				String assessmentSummary = node.path("assessmentSummary").asText("");

				log.info("Assessment completed for patient: {}", patientName);

				if (!patientEmail.isEmpty()) {
					emailService.sendAssessmentEmail(patientEmail, patientName, doctorName, assessmentSummary);
				}

				if (!patientPhone.isEmpty()) {
					smsService.sendAssessmentSMS(patientPhone, patientName, doctorName);
				}
			}
		} catch (Exception e) {
			log.error("Error processing notification message: {}", e.getMessage());
		}
	}
}
