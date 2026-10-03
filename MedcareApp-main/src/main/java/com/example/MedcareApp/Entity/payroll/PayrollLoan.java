package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_loans")
public class PayrollLoan {
    @Id
    private String id;
    private String employeeType;
    private String employeeId;
    private BigDecimal amount;
    private BigDecimal emi;
    private BigDecimal balance;
    private String status = "ACTIVE";
    private String reason;
    private String createdBy;
    private List<String> paidCycleIds = new ArrayList<>();
    private Instant createdAt = Instant.now();
}
