package com.medora.kafka.medoraAI.rag.model;

import java.time.LocalDateTime;

public class FeedbackRequest {

	private long id;
	private String question;
	private String rating; // "positive" or "negative"
	private String comment;
	private LocalDateTime createdAt;

	public long getId() { return id; }
	public void setId(long id) { this.id = id; }
	public String getQuestion() { return question; }
	public void setQuestion(String question) { this.question = question; }
	public String getRating() { return rating; }
	public void setRating(String rating) { this.rating = rating; }
	public String getComment() { return comment; }
	public void setComment(String comment) { this.comment = comment; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
