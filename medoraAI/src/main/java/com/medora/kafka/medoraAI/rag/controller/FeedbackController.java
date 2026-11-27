package com.medora.kafka.medoraAI.rag.controller;

import com.medora.assistant.feedback.RagFeedbackEntity;
import com.medora.assistant.feedback.RagFeedbackRepository;
import com.medora.kafka.medoraAI.rag.model.FeedbackRequest;
import com.medora.kafka.medoraAI.rag.model.FeedbackSummary;
import com.medora.kafka.medoraAI.rag.model.FeedbackSummary.TopQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

	private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

	private final RagFeedbackRepository feedbackRepository;

	public FeedbackController(RagFeedbackRepository feedbackRepository) {
		this.feedbackRepository = feedbackRepository;
	}

	@PostMapping
	public ResponseEntity<Map<String, String>> submitFeedback(@RequestBody FeedbackRequest request) {
		RagFeedbackEntity entity = new RagFeedbackEntity();
		entity.setQuestion(request.getQuestion());
		entity.setRating(request.getRating());
		entity.setComment(request.getComment());
		entity.setCreatedAt(LocalDateTime.now());
		feedbackRepository.save(entity);
		log.info("Feedback saved to DB: {} for question '{}'", request.getRating(), request.getQuestion());
		Map<String, String> response = new HashMap<>();
		response.put("status", "success");
		response.put("message", "Thank you for your feedback!");
		return ResponseEntity.ok(response);
	}

	@GetMapping("/summary")
	public ResponseEntity<FeedbackSummary> getSummary() {
		long positive = feedbackRepository.countByRating("positive");
		long negative = feedbackRepository.countByRating("negative");
		long total = positive + negative;
		double satisfactionRate = total > 0 ? (double) positive / total * 100 : 0;

		// Top questions by feedback count (top 5)
		List<Object[]> topRows = feedbackRepository.findTopQuestions();
		List<TopQuestion> topQuestions = topRows.stream()
			.limit(5)
			.map(row -> new TopQuestion((String) row[0], ((Number) row[1]).longValue()))
			.collect(Collectors.toList());

		FeedbackSummary summary = new FeedbackSummary();
		summary.setTotalFeedback((int) total);
		summary.setPositiveFeedback((int) positive);
		summary.setNegativeFeedback((int) negative);
		summary.setSatisfactionRate(Math.round(satisfactionRate * 10) / 10.0);
		summary.setTopQuestions(topQuestions);
		return ResponseEntity.ok(summary);
	}
}
