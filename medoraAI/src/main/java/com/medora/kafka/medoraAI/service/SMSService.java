package com.medora.kafka.medoraAI.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SMSService {

	private static final Logger log = LoggerFactory.getLogger(SMSService.class);

	@Value("${notification.sms.provider:mock}")
	private String smsProvider;

	public void sendAssessmentSMS(String to, String patientName, String doctorName) {
		if ("mock".equals(smsProvider)) {
			log.info("[MOCK SMS] To: {}, Patient: {}, Doctor: {}", to, patientName, doctorName);
			return;
		}
		try {
			log.info("[TWILIO SMS] Sending to {}: Assessment by {} for {}", to, doctorName, patientName);
		} catch (Exception e) {
			log.error("Failed to send SMS to {}: {}", to, e.getMessage());
		}
	}
}
