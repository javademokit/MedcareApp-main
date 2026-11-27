package com.medora.kafka.medoraAI.controller;

import com.medora.kafka.medoraAI.producer.KafkaProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kafka")
public class KafkaController {

	private final KafkaProducerService producerService;

	public KafkaController(KafkaProducerService producerService) {
		this.producerService = producerService;
	}

	@PostMapping("/send")
	public ResponseEntity<String> send(@RequestParam String message) {
		producerService.send(message);
		return ResponseEntity.ok("Message sent: " + message);
	}
}
