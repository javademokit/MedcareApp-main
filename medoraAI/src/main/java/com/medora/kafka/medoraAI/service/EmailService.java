package com.medora.kafka.medoraAI.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	@Autowired(required = false)
	private JavaMailSender mailSender;

	public void sendAssessmentEmail(String to, String patientName, String doctorName, String assessmentSummary) {
		if (mailSender == null) {
			log.warn("JavaMailSender not configured — skipping email to {}", to);
			return;
		}
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true);
			helper.setTo(to);
			helper.setSubject("Assessment Completed - Medora AI");
			helper.setText(buildEmailBody(patientName, doctorName, assessmentSummary), true);
			mailSender.send(message);
			log.info("Email sent to {}", to);
		} catch (MailException | MessagingException e) {
			log.error("Failed to send email to {}: {}", to, e.getMessage());
		}
	}

	private String buildEmailBody(String patientName, String doctorName, String assessmentSummary) {
		return """
			<html>
			<body style="font-family: Arial, sans-serif; padding: 20px;">
				<h2 style="color: #00897B;">Assessment Completed</h2>
				<p>Dear %s,</p>
				<p>Your assessment with %s has been completed.</p>
				<p><strong>Summary:</strong> %s</p>
				<p>Please contact your doctor if you have any questions.</p>
				<p>Best regards,<br><strong>Medora AI</strong></p>
			</body>
			</html>
			""".formatted(patientName, doctorName, assessmentSummary);
	}
}
