package com.medora.kafka.medoraAI.rag.controller;

import com.medora.kafka.medoraAI.rag.model.QuestionResponse;
import com.medora.kafka.medoraAI.rag.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	@PostMapping("/ask")
	public ResponseEntity<QuestionResponse> ask(@RequestBody com.medora.kafka.medoraAI.rag.model.QuestionRequest request) {
		return ResponseEntity.ok(ragService.ask(request.question()));
	}

	@GetMapping("/ask")
	public ResponseEntity<QuestionResponse> ask(@RequestParam String question) {
		return ResponseEntity.ok(ragService.ask(question));
	}
}
