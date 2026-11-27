package com.medora.assistant.feedback;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rag_feedback")
public class RagFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String rating; // "positive" or "negative"

    @Column
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public RagFeedbackEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
