package com.example.MedcareApp.Entity.hrpayroll;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_payroll_audit_logs")
public class PayrollAuditLog {
    @Id
    private String id = UUID.randomUUID().toString();
    private String entityType = "PAYROLL_RUN";
    private String entityId;
    private String action;
    private String actor;
    private String reason;
    private Map<String, Object> details = new LinkedHashMap<>();
    private Instant createdAt = Instant.now();
}
