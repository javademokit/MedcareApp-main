package com.example.payroll.dto;

import com.example.payroll.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record LineDto(String name, BigDecimal amount) {}
    public record ComponentDto(String name, ComponentKind kind, CalcType calcType, BigDecimal value, BigDecimal cap) {}
    public record StructureDto(Long id, String name, BigDecimal basicPct, List<ComponentDto> components) {}
    public record PreviewRequest(StructureDto structure, BigDecimal monthlyGross) {}

    public record EmployeeDto(Long id, String code, String name, String department, String role,
                              BigDecimal monthlyGross, String bank, String bankAccountNumber, String ifscCode,
                              String bankBranchName, String bankCity, Long structureId, String structureName,
                              LocalDate joiningDate, String employmentType, String workEmail, String phone,
                              String emergencyContactName, String emergencyContactPhone,
                              String registrationCouncil, String professionalRegistrationNo,
                              LocalDate registrationExpiryDate, Boolean identityVerified,
                              Boolean professionalCredentialVerified, Boolean contractSigned,
                              Boolean orientationComplete, Boolean systemAccessProvisioned) {}
    public record EmployeeDocumentDto(String type, String label, String contentType, long size, Instant uploadedAt) {}
    public record EmployeeProfilePictureDto(String contentType, long size, Instant uploadedAt) {}

    public record AttendanceUpdate(Long employeeId, LocalDate date, AttendanceStatus status) {}
    public record EmployeeAttendance(Long employeeId, String code, String name, String department,
                                     String role, List<String> days) {}
    public record AttendanceMonth(String month, boolean processed, int daysInMonth, List<EmployeeAttendance> employees) {}

    public record PayrollRow(Long employeeId, String code, String name, String department, String role, String bank,
                             String structureName, BigDecimal monthlyGross, int totalDays,
                             BigDecimal paidDays, BigDecimal lopDays, BigDecimal weeklyOffDays,
                             List<LineDto> earnings, List<LineDto> deductions,
                             BigDecimal gross, BigDecimal totalDeductions, BigDecimal net, boolean overAllocated) {}

    public record PayrollMonth(String month, boolean processed, String runMode, LocalDateTime runAt,
                               BigDecimal gross, BigDecimal deductions, BigDecimal net, List<PayrollRow> rows) {}

    public record RunDto(String month, String mode, LocalDateTime runAt, BigDecimal net) {}
    public record Toggle(boolean enabled) {}
}
