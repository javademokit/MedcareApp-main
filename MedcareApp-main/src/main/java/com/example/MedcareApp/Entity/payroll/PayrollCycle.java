package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Data
@Document(collection = "payroll_cycles")
@CompoundIndex(name = "payroll_type_month_year_unique", def = "{'employeeType': 1, 'month': 1, 'year': 1}", unique = true)
public class PayrollCycle {
    @Id
    private String id;
    private String employeeType;
    private int month;
    private int year;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String status = "DRAFT";
    private String runBy;
    private String approvedBy;
    private String exceptionsReviewedBy;
    private String exceptionsReviewNote;
    private Instant lockedAt;
    private BigDecimal grossTotal = BigDecimal.ZERO;
    private BigDecimal deductionTotal = BigDecimal.ZERO;
    private BigDecimal netTotal = BigDecimal.ZERO;
    private List<String> exceptions = new ArrayList<>();
    private List<Entry> entries = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @Data
    public static class Entry {
        private String id = UUID.randomUUID().toString();
        private String employeeType;
        private String employeeId;
        private String employeeCode;
        private String employeeName;
        private String structureId;
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal totalDeductions = BigDecimal.ZERO;
        private BigDecimal netPay = BigDecimal.ZERO;
        private String status = "CALCULATED";
        private List<EntryLine> lines = new ArrayList<>();
    }

    @Data
    public static class EntryLine {
        private String componentCode;
        private String componentName;
        private String type;
        private BigDecimal amount = BigDecimal.ZERO;
        private String remarks;
    }
}
