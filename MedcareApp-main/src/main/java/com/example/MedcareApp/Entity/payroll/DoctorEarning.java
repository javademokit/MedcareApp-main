package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_doctor_earnings")
public class DoctorEarning {
    @Id
    private String id;
    private String doctorId;
    private String sourceType;
    private String sourceId;
    private LocalDate serviceDate;
    private String patientRef;
    private String service;
    private String visitType;
    private String department;
    private String doctorRole;
    private String billingStatus;
    private BigDecimal billedAmount;
    private BigDecimal doctorShare;
    private String status = "INCLUDED";
    private String reversalReason;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
