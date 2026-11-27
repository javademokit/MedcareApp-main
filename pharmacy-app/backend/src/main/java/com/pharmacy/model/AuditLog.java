package com.pharmacy.model;

import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("auditLogs")
public class AuditLog {
    @Id private String id;
    private LocalDateTime at;
    private String user;
    private String action;
    private String entity;
    private String entityId;
    private String details;
}
