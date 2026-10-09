package com.example.MedcareApp.Entity.hrpayroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_payslips")
public class Payslip {
    @Id
    private String id = UUID.randomUUID().toString();
    private String payrollId;
    private String payrollItemId;
    private String employeeId;
    private String employeeCode;
    private String employeeEmail;
    private String employeeName;
    private String employeeType;
    private String panNumber;
    private String aadhaarLastFour;
    private String pfUanNumber;
    private LocalDate joiningDate;
    private String departmentName;
    private String designationName;
    private String workLocation;
    private String month;
    private int daysInMonth;
    private BigDecimal paidDays = BigDecimal.ZERO;
    private BigDecimal unpaidDays = BigDecimal.ZERO;
    private BigDecimal weeklyOffDays = BigDecimal.ZERO;
    private BigDecimal overtimeHours = BigDecimal.ZERO;
    private BigDecimal grossSalary = BigDecimal.ZERO;
    private BigDecimal totalDeduction = BigDecimal.ZERO;
    private BigDecimal netSalary = BigDecimal.ZERO;
    private List<PayrollRun.ComponentAmount> earnings = new ArrayList<>();
    private List<PayrollRun.ComponentAmount> deductions = new ArrayList<>();
    private Instant generatedDate = Instant.now();
    private Instant paidAt;
}
