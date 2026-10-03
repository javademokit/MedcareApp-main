package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_adjustments")
public class PayrollAdjustment {
    @Id
    private String id;
    private String employeeType;
    private String employeeId;
    private String cycleId;
    private String type;
    private BigDecimal amount;
    private String reason;
    private String status = "PENDING";
    private String approvedBy;
    private Instant createdAt = Instant.now();
}
