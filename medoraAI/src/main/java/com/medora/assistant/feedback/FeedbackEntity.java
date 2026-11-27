package com.medora.assistant.feedback;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback")
public class FeedbackEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "message_id", nullable = false, columnDefinition = "UUID")
    private UUID messageId;

    @Column(name = "user_id", columnDefinition = "UUID")
    private UUID userId;

    @Column(nullable = false)
    private String rating;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public FeedbackEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
