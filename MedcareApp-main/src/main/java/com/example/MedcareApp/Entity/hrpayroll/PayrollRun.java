package com.example.MedcareApp.Entity.hrpayroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private String runType = "REGULAR";
    private int version = 1;
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
    private Instant lockedAt;
    private Instant processedAt;
    private Instant paidAt;
    private String reopenedFrom;
    private String reopenReason;
    private Map<String, Object> inputSnapshot = new LinkedHashMap<>();
    private List<PayrollItem> items = new ArrayList<>();
    private List<PayrollAdjustment> adjustments = new ArrayList<>();

    @Data
    public static class PayrollAdjustment {
        private String employeeId;
        private String type;
        private String code;
        private String name;
        private BigDecimal amount = BigDecimal.ZERO;
        private String reason;
        private String actor;
        private Instant createdAt = Instant.now();
    }

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
        private BigDecimal weeklyOffDays = BigDecimal.ZERO;
        private BigDecimal overtimeHours = BigDecimal.ZERO;
        private boolean salaryOnHold;
        private String holdReason;
        private List<String> flags = new ArrayList<>();
        private List<Map<String, Object>> trace = new ArrayList<>();
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
