package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_doctor_rates")
public class DoctorRateCard {
    @Id
    private String id;
    private String rateType;
    private String doctorId;
    private String serviceCode;
    private String department;
    private String visitType;
    private String doctorRole;
    private BigDecimal fee;
    private BigDecimal sharePercent;
    private BigDecimal fixedAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant createdAt = Instant.now();
}
