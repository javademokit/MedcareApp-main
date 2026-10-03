package com.example.MedcareApp.Entity.payroll;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_audit_logs")
public class PayrollAuditLog {
    @Id
    private String id;
    private String userId;
    private String action;
    private String entity;
    private String entityId;
    private Instant at = Instant.now();
}
