package com.example.MedcareApp.Entity.hrpayroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_payroll_runs")
public class PayrollRun {
    @Id
    private String id = UUID.randomUUID().toString();
    private String month;
    private String status = "DRAFT";
    private BigDecimal totalGross = BigDecimal.ZERO;
    private BigDecimal totalDeduction = BigDecimal.ZERO;
    private BigDecimal totalNet = BigDecimal.ZERO;
    private int employeeCount;
    private String createdBy;
    private String approvedBy;
    private String rejectionReason;
    private Instant createdAt = Instant.now();
    private Instant approvedAt;
    private Instant processedAt;
    private List<PayrollItem> items = new ArrayList<>();

    @Data
    public static class PayrollItem {
        private String employeeId;
        private String employeeCode;
        private String employeeName;
        private String employeeType;
        private String departmentName;
        private BigDecimal grossSalary = BigDecimal.ZERO;
        private BigDecimal totalDeduction = BigDecimal.ZERO;
        private BigDecimal netSalary = BigDecimal.ZERO;
        private int workingDays;
        private BigDecimal paidDays = BigDecimal.ZERO;
        private BigDecimal unpaidDays = BigDecimal.ZERO;
        private BigDecimal overtimeHours = BigDecimal.ZERO;
        private List<ComponentAmount> earnings = new ArrayList<>();
        private List<ComponentAmount> deductions = new ArrayList<>();
    }

    @Data
    public static class ComponentAmount {
        private String code;
        private String name;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
