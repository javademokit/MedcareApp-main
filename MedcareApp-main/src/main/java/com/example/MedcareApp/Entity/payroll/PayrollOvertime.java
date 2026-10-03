package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_nurse_overtime")
public class PayrollOvertime {
    @Id
    private String id;
    private String nurseId;
    private LocalDate date;
    private BigDecimal hours;
    private String wardId;
    private String status = "PENDING";
    private String submittedBy;
    private String approvedBy;
    private String decisionNote;
    private Instant createdAt = Instant.now();
}
