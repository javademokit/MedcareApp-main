package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_doctor_profiles")
public class DoctorPayProfile {
    @Id
    private String id;
    private String doctorId;
    private String doctorType;
    private String payModel;
    private BigDecimal guaranteedMinimum;
    private BigDecimal variableCap;
    private BigDecimal onCallAllowance;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
