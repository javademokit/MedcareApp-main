package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_nurse_ward_allowances")
public class NurseWardAllowance {
    @Id
    private String id;
    private String wardId;
    private BigDecimal amount;
    private String basis;
    private LocalDate effectiveFrom;
    private Instant createdAt = Instant.now();
}
