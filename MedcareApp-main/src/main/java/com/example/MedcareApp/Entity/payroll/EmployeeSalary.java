package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_employee_salaries")
public class EmployeeSalary {
    @Id
    private String id;
    private String employeeType;
    private String employeeId;
    private String structureId;
    private BigDecimal basic = BigDecimal.ZERO;
    private BigDecimal monthlyHours;
    private BigDecimal overtimeMultiplier;
    private BigDecimal lopDivisor;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String createdBy;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
