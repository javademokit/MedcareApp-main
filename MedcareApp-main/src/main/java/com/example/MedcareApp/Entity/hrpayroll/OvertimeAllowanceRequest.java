package com.example.MedcareApp.Entity.hrpayroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_overtime_allowance_requests")
public class OvertimeAllowanceRequest {
    @Id
    private String id = UUID.randomUUID().toString();
    private String employeeId;
    private String employeeCode;
    private String employeeName;
    private String month;
    private LocalDate overtimeDate;
    private BigDecimal hours = BigDecimal.ZERO;
    private String reason;
    private String status = "PENDING";
    private BigDecimal approvedAmount;
    private String approvedBy;
    private Instant approvedAt;
    private Instant createdAt = Instant.now();
}
