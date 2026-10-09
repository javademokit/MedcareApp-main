package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.hrpayroll.Attendance;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.hrpayroll.Department;
import com.example.MedcareApp.Entity.hrpayroll.Designation;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.hrpayroll.EmployeeType;
import com.example.MedcareApp.Entity.hrpayroll.LeaveRequest;
import com.example.MedcareApp.Entity.hrpayroll.OvertimeAllowanceRequest;
import com.example.MedcareApp.Entity.hrpayroll.PayrollAuditLog;
import com.example.MedcareApp.Entity.hrpayroll.PayrollRun;
import com.example.MedcareApp.Entity.hrpayroll.Payslip;
import com.example.MedcareApp.Entity.hrpayroll.SalaryComponent;
import com.example.MedcareApp.Entity.hrpayroll.SalaryStructure;
import com.example.MedcareApp.Entity.hrpayroll.Shift;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrPayrollService {
    private static final Map<String, String> DEFAULT_EMPLOYEE_TYPES = Map.ofEntries(
            Map.entry("DOCTOR", "Doctor"), Map.entry("NURSE", "Nurse"),
            Map.entry("TECHNICIAN", "Technician"), Map.entry("OPERATOR", "Operator"),
            Map.entry("PHARMACIST", "Pharmacist"), Map.entry("RECEPTIONIST", "Receptionist"),
            Map.entry("ACCOUNTANT", "Accountant"), Map.entry("ADMIN", "Admin"),
            Map.entry("LAB_ASSISTANT", "Lab Assistant"), Map.entry("OTHER", "Other"));
    private static final Set<String> EMPLOYEE_STATUSES = Set.of(
            "DRAFT", "ONBOARDING", "ACTIVE", "ON_LEAVE", "NOTICE_PERIOD", "RESIGNED", "TERMINATED", "RETIRED");
    private static final Set<String> EMPLOYMENT_TYPES = Set.of(
            "FULL_TIME", "PART_TIME", "CONTRACT", "TEMPORARY", "INTERN");
    private static final Set<String> ATTENDANCE_STATUSES = Set.of(
            "PRESENT", "ABSENT", "HALF_DAY", "LEAVE", "HOLIDAY", "WEEK_OFF");
    private static final Set<String> LEAVE_TYPES = Set.of(
            "CASUAL", "SICK", "ANNUAL", "MATERNITY", "PATERNITY", "EMERGENCY", "UNPAID");
    private static final Set<String> COMPONENT_TYPES = Set.of("EARNING", "DEDUCTION");
    private static final Set<String> CALCULATION_TYPES = Set.of("FIXED", "PERCENTAGE", "FORMULA");

    private final MongoTemplate mongo;

    public HrPayrollService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Map<String, Object> dashboard(String monthValue) {
        YearMonth month = parseMonth(monthValue);
        List<Employee> employees = mongo.findAll(Employee.class);
        List<Employee> activeEmployees = employees.stream()
                .filter(employee -> "ACTIVE".equals(employee.getStatus())).toList();
        LocalDate today = LocalDate.now();
        List<Attendance> todayAttendance = mongo.find(
                Query.query(Criteria.where("attendanceDate").is(today)), Attendance.class);
        PayrollRun run = payrollForMonth(month.toString());
        Map<String, Object> payrollSummary = new LinkedHashMap<>();
        payrollSummary.put("month", month.toString());
        payrollSummary.put("status", run == null ? "NOT STARTED" : run.getStatus());
        payrollSummary.put("totalGross", run == null ? zero() : run.getTotalGross());
        payrollSummary.put("totalDeduction", run == null ? zero() : run.getTotalDeduction());
        payrollSummary.put("totalNet", run == null ? zero() : run.getTotalNet());

        Map<String, Object> attendanceSummary = new LinkedHashMap<>();
        attendanceSummary.put("present", countStatus(todayAttendance, "PRESENT"));
        attendanceSummary.put("absent", countStatus(todayAttendance, "ABSENT"));
        attendanceSummary.put("onLeave", countStatus(todayAttendance, "LEAVE"));

        Map<String, Object> pendingActions = new LinkedHashMap<>();
        pendingActions.put("onboarding", employees.stream().filter(e -> "ONBOARDING".equals(e.getStatus())).count());
        pendingActions.put("leaveApprovals", mongo.find(
                Query.query(Criteria.where("status").is("PENDING")), LeaveRequest.class).size());
        pendingActions.put("payrollApprovals", run != null && "CALCULATED".equals(run.getStatus()) ? 1 : 0);
        pendingActions.put("documents", employees.stream()
                .filter(e -> e.getDocumentLinks() == null || e.getDocumentLinks().isEmpty()).count());

        long newJoiners = mongo.find(Query.query(Criteria.where("joiningDate")
                        .gte(month.atDay(1)).lte(month.atEndOfMonth())), Employee.class).size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEmployees", mongo.findAll(Employee.class).size());
        result.put("activeEmployees", activeEmployees.size());
        result.put("onLeave", employees.stream().filter(e -> "ON_LEAVE".equals(e.getStatus())).count());
        result.put("noticePeriod", employees.stream().filter(e -> "NOTICE_PERIOD".equals(e.getStatus())).count());
        result.put("newJoiners", newJoiners);
        result.put("payrollSummary", payrollSummary);
        result.put("attendanceToday", attendanceSummary);
        result.put("pendingActions", pendingActions);
        return result;
    }

    public List<EmployeeType> employeeTypes() {
        List<EmployeeType> configuredTypes = mongo.findAll(EmployeeType.class);
        Set<String> configuredCodes = new HashSet<>();
        configuredTypes.forEach(type -> configuredCodes.add(upper(type.getCode())));
        DEFAULT_EMPLOYEE_TYPES.forEach((code, name) -> {
            if (!configuredCodes.contains(code)) {
                EmployeeType type = new EmployeeType();
                type.setCode(code);
                type.setName(name);
                type.setStatus("ACTIVE");
                mongo.save(type);
            }
        });
        return mongo.findAll(EmployeeType.class);
    }

    public EmployeeType saveEmployeeType(EmployeeType type) {
        requireText(type.getName(), "Employee type name is required");
        requireText(type.getCode(), "Employee type code is required");
        String code = upper(type.getCode());
        if (!code.matches("[A-Z][A-Z0-9_]{1,39}")) {
            throw badRequest("Employee type code must use 2-40 uppercase letters, digits, or underscores");
        }
        ensureUniqueCode(EmployeeType.class, code, type.getId(), "Employee type");
        type.setCode(code);
        type.setStatus(defaultValue(upper(type.getStatus()), "ACTIVE"));
        validateActiveStatus(type.getStatus());
        return mongo.save(type);
    }

    public void deactivateEmployeeType(String id) {
        EmployeeType type = require(EmployeeType.class, id, "Employee type");
        if (mongo.findOne(Query.query(Criteria.where("employeeType").is(type.getCode())
                .and("status").ne("TERMINATED")), Employee.class) != null) {
            throw conflict("Employee types in use cannot be deactivated");
        }
        type.setStatus("INACTIVE");
        mongo.save(type);
    }

    public List<Map<String, Object>> employees() {
        return mongo.findAll(Employee.class).stream().map(this::employeeView).toList();
    }

    public Employee employee(String id) {
        return require(Employee.class, id, "Employee");
    }

    public List<Map<String, Object>> doctorEmployees() {
        return mongo.findAll(Employee.class).stream()
                .filter(employee -> "DOCTOR".equals(upper(employee.getEmployeeType())))
                .filter(employee -> !"TERMINATED".equals(upper(employee.getStatus())))
                .map(employee -> {
                    Doctor profile = findDoctorProfile(employee);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", employee.getId());
                    result.put("employeeCode", employee.getEmployeeCode());
                    result.put("doctorName", employee.getFullName());
                    result.put("doctorSpecialistName", doctorSpecialization(employee));
                    result.put("doctorMobileNo", employee.getMobile());
                    result.put("doctorDestination", employeeDepartmentName(employee));
                    result.put("doctorfee", employee.getDoctorConsultationFee() != null
                            ? employee.getDoctorConsultationFee()
                            : profile == null ? 0 : profile.getDoctorfee());
                    result.put("doctorAvailabletime", employee.getDoctorAvailableTimes() != null
                            && !employee.getDoctorAvailableTimes().isEmpty()
                            ? employee.getDoctorAvailableTimes()
                            : profile == null ? List.of() : profile.getDoctorAvailabletime());
                    result.put("doctorProfileId", employee.getDoctorProfileId());
                    result.put("status", employee.getStatus());
                    return result;
                })
                .toList();
    }

    public Doctor updateDoctorSchedule(String employeeId, List<String> availableTimes, Double consultationFee) {
        Employee employee = require(Employee.class, employeeId, "Employee");
        if (!"DOCTOR".equals(upper(employee.getEmployeeType()))) {
            throw badRequest("Scheduling can only be updated for a doctor employee");
        }
        if ("TERMINATED".equals(upper(employee.getStatus()))) {
            throw conflict("A terminated doctor cannot be scheduled");
        }
        if (availableTimes == null || availableTimes.isEmpty()) {
            throw badRequest("Add at least one available time slot");
        }
        List<String> normalizedTimes = availableTimes.stream()
                .filter(HrPayrollService::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (normalizedTimes.isEmpty()) {
            throw badRequest("Add at least one available time slot");
        }
        for (String time : normalizedTimes) {
            try {
                java.time.LocalTime.parse(time);
            } catch (java.time.format.DateTimeParseException exception) {
                throw badRequest("Use HH:mm for each available time slot");
            }
        }
        employee.setDoctorAvailableTimes(normalizedTimes);
        if (consultationFee != null) {
            employee.setDoctorConsultationFee(nonNegative(BigDecimal.valueOf(consultationFee)).doubleValue());
        }
        employee.setUpdatedAt(Instant.now());
        syncDoctorProfile(employee);
        mongo.save(employee);
        return mongo.findById(employee.getDoctorProfileId(), Doctor.class);
    }

    private String doctorSpecialization(Employee employee) {
        Object specialization = employee.getProfessionalInfo() == null
                ? null : employee.getProfessionalInfo().get("specialization");
        if (specialization instanceof String value && hasText(value)) return value.trim();
        return "General medicine";
    }

    public Map<String, String> attachEmployeeDocument(
            String employeeId, String documentType, String fileId, String fileName, String contentType) {
        Employee employee = require(Employee.class, employeeId, "Employee");
        if (employee.getOnboardingDocuments() == null) {
            employee.setOnboardingDocuments(new LinkedHashMap<>());
        }
        Map<String, String> document = Map.of(
                "fileId", fileId,
                "fileName", fileName,
                "contentType", contentType);
        employee.getOnboardingDocuments().put(documentType, document);
        employee.setUpdatedAt(Instant.now());
        mongo.save(employee);
        return document;
    }

    public Employee saveEmployee(Employee employee) {
        validateEmployee(employee, null);
        employee.setEmployeeCode(normalize(employee.getEmployeeCode()));
        normalizeStatutoryIdentifiers(employee);
        if (employee.getEmployeeCode() == null) {
            String employeeType = upper(employee.getEmployeeType());
            String prefix = "DOCTOR".equals(employeeType) ? "DT"
                    : "NURSE".equals(employeeType) ? "NR" : "EMP";
            employee.setEmployeeCode(StaffIdentifierGenerator.generate(prefix));
        }
        employee.setEmployeeType(upper(employee.getEmployeeType()));
        employee.setDepartmentName(employeeDepartmentName(employee));
        employee.setStatus(defaultValue(upper(employee.getStatus()), "ONBOARDING"));
        employee.setEmploymentType(defaultValue(upper(employee.getEmploymentType()), "FULL_TIME"));
        employee.setEmail(normalizeEmail(employee.getEmail()));
        employee.setUpdatedAt(Instant.now());
        syncDoctorProfile(employee);
        return mongo.save(employee);
    }

    public Employee updateEmployee(String id, Employee update) {
        Employee current = employee(id);
        if (!hasText(update.getAadhaarLastFour())) update.setAadhaarLastFour(current.getAadhaarLastFour());
        if (update.getOnboardingDocuments() == null || update.getOnboardingDocuments().isEmpty()) {
            update.setOnboardingDocuments(current.getOnboardingDocuments());
        }
        validateEmployee(update, id);
        normalizeStatutoryIdentifiers(update);
        update.setId(id);
        update.setEmployeeCode(current.getEmployeeCode());
        update.setCreatedAt(current.getCreatedAt());
        update.setUpdatedAt(Instant.now());
        update.setEmployeeType(upper(update.getEmployeeType()));
        update.setDepartmentName(employeeDepartmentName(update));
        update.setStatus(defaultValue(upper(update.getStatus()), current.getStatus()));
        update.setEmploymentType(defaultValue(upper(update.getEmploymentType()), current.getEmploymentType()));
        update.setEmail(normalizeEmail(update.getEmail()));
        syncDoctorProfile(update);
        return mongo.save(update);
    }

    private void syncDoctorProfile(Employee employee) {
        if (!"DOCTOR".equals(upper(employee.getEmployeeType()))) return;

        Doctor doctor = findDoctorProfile(employee);
        boolean creatingProfile = doctor == null;
        if (doctor == null) {
            doctor = new Doctor();
            doctor.setDoctorAvailabletime(new ArrayList<>());
            doctor.setDoctorfee(0);
        }

        doctor.setEmployeeId(employee.getEmployeeCode().startsWith("DT-")
                ? employee.getEmployeeCode() : StaffIdentifierGenerator.generate("DT"));
        doctor.setDoctorName(employee.getFullName());
        doctor.setDoctorMobileNo(employee.getMobile());
        Object specialization = employee.getProfessionalInfo() == null
                ? null : employee.getProfessionalInfo().get("specialization");
        if (specialization instanceof String value && hasText(value)) {
            doctor.setDoctorSpecialistName(value.trim());
        } else if (creatingProfile) {
            doctor.setDoctorSpecialistName("General medicine");
        }
        doctor.setDoctorDestination(employeeDepartmentName(employee));
        if (employee.getDoctorConsultationFee() != null) {
            doctor.setDoctorfee(nonNegative(BigDecimal.valueOf(employee.getDoctorConsultationFee())).doubleValue());
        }
        if (employee.getDoctorAvailableTimes() != null) {
            List<String> times = employee.getDoctorAvailableTimes().stream()
                    .filter(HrPayrollService::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            doctor.setDoctorAvailabletime(times);
        }
        doctor.setDoctorslot(doctor.getDoctorAvailabletime() == null ? 0 : doctor.getDoctorAvailabletime().size());
        Doctor savedDoctor = mongo.save(doctor);
        employee.setDoctorProfileId(savedDoctor.getId());
    }

    private Doctor findDoctorProfile(Employee employee) {
        Doctor doctor = hasText(employee.getDoctorProfileId())
                ? mongo.findById(employee.getDoctorProfileId(), Doctor.class) : null;
        if (doctor == null && hasText(employee.getEmployeeCode())) {
            doctor = mongo.findOne(Query.query(Criteria.where("employeeId").is(employee.getEmployeeCode())),
                    Doctor.class);
        }
        return doctor;
    }

    public void deactivateEmployee(String id) {
        Employee employee = employee(id);
        employee.setStatus("TERMINATED");
        employee.setUpdatedAt(Instant.now());
        mongo.save(employee);
    }

    public List<Department> departments() {
        return mongo.findAll(Department.class);
    }

    public Department saveDepartment(Department department) {
        requireText(department.getName(), "Department name is required");
        requireText(department.getCode(), "Department code is required");
        String code = upper(department.getCode());
        ensureUniqueCode(Department.class, code, department.getId(), "Department");
        department.setCode(code);
        department.setStatus(defaultValue(upper(department.getStatus()), "ACTIVE"));
        validateActiveStatus(department.getStatus());
        return mongo.save(department);
    }

    public void deactivateDepartment(String id) {
        Department department = require(Department.class, id, "Department");
        boolean hasEmployees = mongo.findOne(Query.query(Criteria.where("departmentId").is(id)), Employee.class) != null;
        boolean hasDesignations = mongo.findOne(Query.query(Criteria.where("departmentId").is(id)), Designation.class) != null;
        if (hasEmployees || hasDesignations) throw conflict("Departments with employees or designations cannot be deactivated");
        department.setStatus("INACTIVE");
        mongo.save(department);
    }

    public List<Map<String, Object>> designations() {
        return mongo.findAll(Designation.class).stream().map(item -> {
            Map<String, Object> row = beanMap(item);
            Department department = item.getDepartmentId() == null ? null
                    : mongo.findById(item.getDepartmentId(), Department.class);
            row.put("departmentName", department == null ? null : department.getName());
            return row;
        }).toList();
    }

    public Designation saveDesignation(Designation designation) {
        requireText(designation.getName(), "Designation name is required");
        requireText(designation.getCode(), "Designation code is required");
        String code = upper(designation.getCode());
        ensureUniqueCode(Designation.class, code, designation.getId(), "Designation");
        if (hasText(designation.getDepartmentId())) {
            Department department = require(Department.class, designation.getDepartmentId(), "Department");
            if (!"ACTIVE".equals(department.getStatus())) throw badRequest("Designation department is inactive");
        }
        designation.setCode(code);
        designation.setStatus(defaultValue(upper(designation.getStatus()), "ACTIVE"));
        validateActiveStatus(designation.getStatus());
        return mongo.save(designation);
    }

    public void deactivateDesignation(String id) {
        Designation item = require(Designation.class, id, "Designation");
        if (mongo.findOne(Query.query(Criteria.where("designationId").is(id)), Employee.class) != null) {
            throw conflict("Designations assigned to employees cannot be deactivated");
        }
        item.setStatus("INACTIVE");
        mongo.save(item);
    }

    public List<Shift> shifts() {
        return mongo.findAll(Shift.class);
    }

    public Shift saveShift(Shift shift) {
        requireText(shift.getName(), "Shift name is required");
        requireText(shift.getStartTime(), "Shift start time is required");
        requireText(shift.getEndTime(), "Shift end time is required");
        if (shift.getBreakMinutes() < 0) throw badRequest("Break minutes cannot be negative");
        shift.setStatus(defaultValue(upper(shift.getStatus()), "ACTIVE"));
        validateActiveStatus(shift.getStatus());
        return mongo.save(shift);
    }

    public void deactivateShift(String id) {
        Shift shift = require(Shift.class, id, "Shift");
        if (mongo.findOne(Query.query(Criteria.where("shiftId").is(id)), Employee.class) != null) {
            throw conflict("Shifts assigned to employees cannot be deactivated");
        }
        shift.setStatus("INACTIVE");
        mongo.save(shift);
    }

    public List<Map<String, Object>> attendance(String monthValue) {
        YearMonth month = parseMonth(monthValue);
        Query query = Query.query(Criteria.where("attendanceDate")
                .gte(month.atDay(1)).lte(month.atEndOfMonth()));
        return mongo.find(query, Attendance.class).stream().map(this::attendanceView).toList();
    }

    public List<Map<String, Object>> attendanceForEmployee(String employeeId) {
        require(Employee.class, employeeId, "Employee");
        return mongo.find(Query.query(Criteria.where("employeeId").is(employeeId)), Attendance.class)
                .stream().map(this::attendanceView).toList();
    }

    public Attendance saveAttendance(Attendance attendance) {
        Employee employee = require(Employee.class, attendance.getEmployeeId(), "Employee");
        if (!Set.of("ACTIVE", "ON_LEAVE", "NOTICE_PERIOD").contains(employee.getStatus())) {
            throw badRequest("Attendance can only be recorded for an employed employee");
        }
        if (attendance.getAttendanceDate() == null) throw badRequest("Attendance date is required");
        attendance.setStatus(upper(attendance.getStatus()));
        if (!ATTENDANCE_STATUSES.contains(attendance.getStatus())) throw badRequest("Unsupported attendance status");
        attendance.setWorkedHours(nonNegative(attendance.getWorkedHours()));
        attendance.setOvertimeHours(nonNegative(attendance.getOvertimeHours()));
        Query duplicate = Query.query(Criteria.where("employeeId").is(employee.getId())
                .and("attendanceDate").is(attendance.getAttendanceDate()));
        Attendance existing = mongo.findOne(duplicate, Attendance.class);
        if (existing != null && !existing.getId().equals(attendance.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance is already recorded for this employee and date");
        }
        attendance.setEmployeeId(employee.getId());
        return mongo.save(attendance);
    }

    public List<Map<String, Object>> leaves(String monthValue) {
        YearMonth month = parseMonth(monthValue);
        Query query = Query.query(Criteria.where("fromDate").lte(month.atEndOfMonth())
                .and("toDate").gte(month.atDay(1)));
        return mongo.find(query, LeaveRequest.class).stream().map(this::leaveView).toList();
    }

    public List<Map<String, Object>> myLeaves(String email) {
        Employee employee = employeeForEmail(email);
        return mongo.find(Query.query(Criteria.where("employeeId").is(employee.getId())), LeaveRequest.class)
                .stream().map(this::leaveView).toList();
    }

    public LeaveRequest createLeave(LeaveRequest leave, String principalEmail, boolean manager) {
        if (leave.getFromDate() == null || leave.getToDate() == null || leave.getToDate().isBefore(leave.getFromDate())) {
            throw badRequest("Leave dates are invalid");
        }
        leave.setLeaveType(upper(leave.getLeaveType()));
        if (!LEAVE_TYPES.contains(leave.getLeaveType())) throw badRequest("Unsupported leave type");
        if (manager) {
            Employee target = require(Employee.class, leave.getEmployeeId(), "Employee");
            leave.setEmployeeId(target.getId());
        } else {
            leave.setEmployeeId(employeeForEmail(principalEmail).getId());
        }
        requireText(leave.getReason(), "Leave reason is required");
        leave.setStatus("PENDING");
        leave.setApprovedBy(null);
        leave.setApprovedAt(null);
        return mongo.save(leave);
    }

    public LeaveRequest decideLeave(String id, String decision, String approver) {
        LeaveRequest leave = require(LeaveRequest.class, id, "Leave request");
        if (!"PENDING".equals(leave.getStatus())) throw conflict("Only pending leave requests can be decided");
        String normalized = upper(decision);
        if (!Set.of("APPROVE", "REJECT").contains(normalized)) throw badRequest("Decision must be approve or reject");
        leave.setStatus(normalized.equals("APPROVE") ? "APPROVED" : "REJECTED");
        leave.setApprovedBy(approver);
        leave.setApprovedAt(Instant.now());
        return mongo.save(leave);
    }

    public List<Map<String, Object>> overtimeAllowanceRequests(String monthValue) {
        String month = parseMonth(monthValue).toString();
        return mongo.find(Query.query(Criteria.where("month").is(month)), OvertimeAllowanceRequest.class)
                .stream().map(this::overtimeAllowanceView).toList();
    }

    public List<Map<String, Object>> myOvertimeAllowanceRequests(String email) {
        Employee employee = employeeForEmail(email);
        return mongo.find(Query.query(Criteria.where("employeeId").is(employee.getId())),
                        OvertimeAllowanceRequest.class)
                .stream().map(this::overtimeAllowanceView).toList();
    }

    public OvertimeAllowanceRequest requestOvertimeAllowance(
            LocalDate overtimeDate, BigDecimal hours, String reason, String email) {
        if (overtimeDate == null) throw badRequest("Overtime date is required");
        if (overtimeDate.isAfter(LocalDate.now())) throw badRequest("Overtime date cannot be in the future");
        if (hours == null || hours.signum() <= 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0) {
            throw badRequest("Overtime hours must be greater than 0 and no more than 24");
        }
        requireText(reason, "Reason is required");
        YearMonth month = YearMonth.from(overtimeDate);
        PayrollRun run = payrollForMonth(month.toString());
        if (run != null && !"DRAFT".equals(run.getStatus())) {
            throw conflict("Overtime requests cannot be added after payroll calculation has started for " + month);
        }
        Employee employee = employeeForEmail(email);
        if (employee.getJoiningDate() != null && overtimeDate.isBefore(employee.getJoiningDate())) {
            throw badRequest("Overtime date cannot be before the employee joining date");
        }
        if (!Set.of("ACTIVE", "ON_LEAVE", "NOTICE_PERIOD").contains(upper(employee.getStatus()))) {
            throw badRequest("Only current employees can request overtime allowances");
        }
        OvertimeAllowanceRequest request = new OvertimeAllowanceRequest();
        request.setEmployeeId(employee.getId());
        request.setEmployeeCode(employee.getEmployeeCode());
        request.setEmployeeName(employee.getFullName());
        request.setMonth(month.toString());
        request.setOvertimeDate(overtimeDate);
        request.setHours(hours.stripTrailingZeros());
        request.setReason(reason.trim());
        request.setStatus("PENDING");
        return mongo.save(request);
    }

    public OvertimeAllowanceRequest decideOvertimeAllowance(
            String id, String decision, BigDecimal approvedAmount, String approver) {
        OvertimeAllowanceRequest request = require(
                OvertimeAllowanceRequest.class, id, "Overtime allowance request");
        if (!"PENDING".equals(request.getStatus())) {
            throw conflict("Only pending overtime allowance requests can be decided");
        }
        PayrollRun run = payrollForMonth(request.getMonth());
        if (run != null && !"DRAFT".equals(run.getStatus())) {
            throw conflict("Overtime requests cannot be changed after payroll calculation has started");
        }
        String normalized = upper(decision);
        if (!Set.of("APPROVE", "REJECT").contains(normalized)) {
            throw badRequest("Decision must be approve or reject");
        }
        if ("APPROVE".equals(normalized)) {
            if (approvedAmount == null || approvedAmount.signum() <= 0) {
                throw badRequest("Enter an approved allowance greater than zero");
            }
            request.setApprovedAmount(round(approvedAmount));
        } else {
            request.setApprovedAmount(null);
        }
        request.setStatus("APPROVE".equals(normalized) ? "APPROVED" : "REJECTED");
        request.setApprovedBy(approver);
        request.setApprovedAt(Instant.now());
        return mongo.save(request);
    }

    public List<SalaryComponent> salaryComponents() {
        return mongo.findAll(SalaryComponent.class);
    }

    public SalaryComponent saveSalaryComponent(SalaryComponent component) {
        requireText(component.getName(), "Salary component name is required");
        requireText(component.getCode(), "Salary component code is required");
        component.setCode(upper(component.getCode()));
        component.setType(upper(component.getType()));
        component.setCalculationType(upper(component.getCalculationType()));
        if (!COMPONENT_TYPES.contains(component.getType())) throw badRequest("Component type must be EARNING or DEDUCTION");
        if (!CALCULATION_TYPES.contains(component.getCalculationType())) throw badRequest("Unsupported component calculation type");
        ensureUniqueCode(SalaryComponent.class, component.getCode(), component.getId(), "Salary component");
        component.setValue(nonNegative(component.getValue()));
        component.setBaseCode(defaultValue(upper(component.getBaseCode()), "BASIC"));
        component.setStatus(defaultValue(upper(component.getStatus()), "ACTIVE"));
        validateActiveStatus(component.getStatus());
        if ("FORMULA".equals(component.getCalculationType())) {
            try {
                PayrollFormula.evaluate(component.getFormula(),
                        formulaVariables(zero(), zero(), zero(), BigDecimal.ONE, 1, BigDecimal.ONE));
            } catch (IllegalArgumentException exception) {
                throw badRequest(exception.getMessage());
            }
        }
        return mongo.save(component);
    }

    public List<Map<String, Object>> salaryStructures(String employeeId) {
        Query query = employeeId == null || employeeId.isBlank() ? new Query()
                : Query.query(Criteria.where("employeeId").is(employeeId));
        return mongo.find(query, SalaryStructure.class).stream().map(this::structureView).toList();
    }

    public SalaryStructure saveSalaryStructure(SalaryStructure structure) {
        require(Employee.class, structure.getEmployeeId(), "Employee");
        if (structure.getEffectiveFrom() == null) throw badRequest("Effective from date is required");
        if (structure.getEffectiveTo() != null && structure.getEffectiveTo().isBefore(structure.getEffectiveFrom())) {
            throw badRequest("Effective to date cannot be before effective from date");
        }
        if (structure.getComponents() == null || structure.getComponents().isEmpty()) {
            throw badRequest("A salary structure must contain at least one component");
        }
        Set<String> componentIds = new HashSet<>();
        for (SalaryStructure.ComponentLine line : structure.getComponents()) {
            if (line == null || !hasText(line.getComponentId())) throw badRequest("Every salary line needs a component");
            SalaryComponent component = require(SalaryComponent.class, line.getComponentId(), "Salary component");
            if (!"ACTIVE".equals(component.getStatus())) throw badRequest("Inactive salary components cannot be assigned");
            if (!componentIds.add(component.getId())) throw badRequest("Salary components cannot be repeated in a structure");
            line.setAmount(nonNegative(line.getAmount()));
            line.setUnits(line.getUnits() == null ? BigDecimal.ONE : nonNegative(line.getUnits()));
        }
        structure.setStatus("ACTIVE");
        List<SalaryStructure> existing = mongo.find(
                Query.query(Criteria.where("employeeId").is(structure.getEmployeeId())
                        .and("status").is("ACTIVE")), SalaryStructure.class);
        for (SalaryStructure prior : existing) {
            if (prior.getId().equals(structure.getId())) continue;
            LocalDate priorEnd = prior.getEffectiveTo() == null ? LocalDate.MAX : prior.getEffectiveTo();
            LocalDate newEnd = structure.getEffectiveTo() == null ? LocalDate.MAX : structure.getEffectiveTo();
            if (!priorEnd.isBefore(structure.getEffectiveFrom()) && !newEnd.isBefore(prior.getEffectiveFrom())) {
                throw conflict("Salary structures for an employee cannot have overlapping effective dates");
            }
        }
        return mongo.save(structure);
    }

    public List<PayrollRun> payrollRuns(String monthValue) {
        Query query = monthValue == null || monthValue.isBlank()
                ? new Query()
                : Query.query(Criteria.where("month").is(parseMonth(monthValue).toString()));
        return mongo.find(query.with(Sort.by(Sort.Direction.DESC, "createdAt")), PayrollRun.class);
    }

    public PayrollRun payrollRun(String id) {
        return require(PayrollRun.class, id, "Payroll run");
    }

    public PayrollRun createPayrollRun(String monthValue, String actor) {
        String month = parseMonth(monthValue).toString();
        PayrollRun existingRun = payrollForMonth(month);
        if (existingRun != null) {
            throw conflict("A payroll run for " + month + " already exists (run ID: " + existingRun.getId()
                    + ", status: " + existingRun.getStatus()
                    + "). The run is still saved. Open Payroll History to continue it; no new run was created.");
        }
        PayrollRun run = new PayrollRun();
        run.setMonth(month);
        run.setStatus("DRAFT");
        run.setCreatedBy(actor);
        run.setInputSnapshot(Map.of("month", month, "runType", run.getRunType()));
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "CREATED", actor, null, Map.of("month", month, "version", saved.getVersion()));
        return saved;
    }

    public PayrollRun calculatePayroll(String id) {
        return calculatePayroll(id, "system");
    }

    public PayrollRun calculatePayroll(String id, String actor) {
        PayrollRun run = payrollRun(id);
        if (!"DRAFT".equals(run.getStatus())) throw conflict("Only draft payroll runs can be calculated");
        YearMonth month = parseMonth(run.getMonth());
        List<Employee> employees = payrollEligibleEmployees().stream()
                .filter(employee -> employee.getJoiningDate() == null
                        || !employee.getJoiningDate().isAfter(month.atEndOfMonth()))
                .toList();
        if (employees.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No eligible employees are set up for " + month
                            + ". Add and activate employees before calculating payroll.");
        }
        List<PayrollRun.PayrollItem> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, BigDecimal> approvedAllowances = approvedOvertimeAllowances(month.toString());
        for (Employee employee : employees) {
            List<SalaryStructure> structures = salaryStructuresForMonth(employee.getId(), month);
            List<SalaryStructureTimeline.Segment> segments =
                    SalaryStructureTimeline.segments(employee, structures, month);
            if (segments.isEmpty()) {
                errors.add(employee.getEmployeeCode() + " (" + employee.getFullName()
                        + ") is missing an active salary structure covering the full payroll month (" + month + ")");
            } else {
                PayrollRun.PayrollItem item = calculateEmployee(employee, segments, month);
                applyOvertimeAllowance(item, approvedAllowances.getOrDefault(employee.getId(), zero()));
                items.add(item);
            }
        }
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payroll cannot be calculated: " + String.join("; ", errors));
        }
        PayrollRun previousRun = payrollForMonth(month.minusMonths(1).toString());
        applyPayrollFlags(items, previousRun);
        run.setItems(items);
        run.setEmployeeCount(items.size());
        run.setTotalGross(sum(items, PayrollRun.PayrollItem::getGrossSalary));
        run.setTotalDeduction(sum(items, PayrollRun.PayrollItem::getTotalDeduction));
        run.setTotalNet(sum(items, PayrollRun.PayrollItem::getNetSalary));
        run.setStatus("CALCULATED");
        run.setInputSnapshot(Map.of(
                "month", run.getMonth(),
                "runType", defaultValue(run.getRunType(), "REGULAR"),
                "employeeCount", items.size(),
                "employees", items.stream().map(item -> Map.of(
                        "employeeId", item.getEmployeeId(),
                        "paidDays", item.getPaidDays(),
                        "unpaidDays", item.getUnpaidDays(),
                        "gross", item.getGrossSalary(),
                        "deductions", item.getTotalDeduction(),
                        "net", item.getNetSalary(),
                        "flags", List.copyOf(item.getFlags()))).toList(),
                "calculatedAt", Instant.now().toString()));
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "CALCULATED", actor, null,
                Map.of("employeeCount", items.size(), "gross", saved.getTotalGross(),
                        "deductions", saved.getTotalDeduction(), "net", saved.getTotalNet()));
        return saved;
    }

    public Map<String, Object> payrollPrechecks(String id) {
        PayrollRun run = payrollRun(id);
        YearMonth month = parseMonth(run.getMonth());
        List<Map<String, Object>> warnings = new ArrayList<>();
        List<Map<String, Object>> blockers = new ArrayList<>();
        List<Employee> employees = payrollEligibleEmployees().stream()
                .filter(employee -> employee.getJoiningDate() == null
                        || !employee.getJoiningDate().isAfter(month.atEndOfMonth()))
                .toList();
        for (Employee employee : employees) {
            Map<String, Object> detail = Map.of(
                    "employeeId", employee.getId(),
                    "employeeCode", defaultValue(employee.getEmployeeCode(), ""),
                    "employeeName", employee.getFullName());
            List<SalaryStructureTimeline.Segment> segments = SalaryStructureTimeline.segments(
                    employee, salaryStructuresForMonth(employee.getId(), month), month);
            if (segments.isEmpty()) {
                Map<String, Object> issue = new LinkedHashMap<>(detail);
                issue.put("message", "No active salary structure covers the full month " + month);
                blockers.add(issue);
            }
            if (!hasText(employee.getPanNumber()) || !hasText(employee.getPfUanNumber())) {
                Map<String, Object> issue = new LinkedHashMap<>(detail);
                issue.put("message", "PAN or PF/UAN details are missing");
                warnings.add(issue);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId());
        result.put("month", run.getMonth());
        result.put("eligibleEmployeeCount", employees.size());
        result.put("blockers", blockers);
        result.put("warnings", warnings);
        result.put("ready", blockers.isEmpty() && !employees.isEmpty());
        return result;
    }

    public PayrollRun setSalaryHold(
            String id, String employeeId, boolean hold, String reason, String actor) {
        PayrollRun run = payrollRun(id);
        if (!"CALCULATED".equals(run.getStatus())) {
            throw conflict("Salary holds can only be changed while the payroll run is calculated and under review");
        }
        PayrollRun.PayrollItem item = payrollItem(run, employeeId);
        if (hold) requireText(reason, "A reason is required to put salary on hold");
        item.setSalaryOnHold(hold);
        item.setHoldReason(hold ? reason.trim() : null);
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, hold ? "SALARY_HOLD_SET" : "SALARY_HOLD_RELEASED", actor,
                hold ? reason.trim() : null, Map.of("employeeId", employeeId));
        return saved;
    }

    public PayrollRun addPayrollAdjustment(
            String id, String employeeId, String type, String code, String name,
            BigDecimal amount, String reason, String actor) {
        PayrollRun run = payrollRun(id);
        if (!"CALCULATED".equals(run.getStatus())) {
            throw conflict("Payroll adjustments can only be made while the run is calculated and under review");
        }
        requireText(reason, "A reason is required for a payroll adjustment");
        requireText(name, "Adjustment name is required");
        String normalizedType = upper(type);
        if (!Set.of("EARNING", "DEDUCTION").contains(normalizedType)) {
            throw badRequest("Adjustment type must be EARNING or DEDUCTION");
        }
        BigDecimal normalizedAmount = nonNegative(amount);
        if (normalizedAmount.signum() == 0) throw badRequest("Adjustment amount must be greater than zero");
        PayrollRun.PayrollItem item = payrollItem(run, employeeId);
        PayrollRun.ComponentAmount line = new PayrollRun.ComponentAmount();
        line.setCode(defaultValue(upper(normalize(code)), "ONE_OFF_ADJUSTMENT"));
        line.setName(name.trim());
        line.setAmount(round(normalizedAmount));
        if ("EARNING".equals(normalizedType)) {
            item.getEarnings().add(line);
            item.setGrossSalary(round(item.getGrossSalary().add(normalizedAmount)));
            item.setNetSalary(round(item.getNetSalary().add(normalizedAmount)));
        } else {
            item.getDeductions().add(line);
            item.setTotalDeduction(round(item.getTotalDeduction().add(normalizedAmount)));
            item.setNetSalary(round(item.getNetSalary().subtract(normalizedAmount)));
        }
        item.getTrace().add(Map.of(
                "componentCode", line.getCode(),
                "componentName", line.getName(),
                "type", normalizedType,
                "source", "ONE_OFF_ADJUSTMENT",
                "reason", reason.trim(),
                "calculatedAmount", line.getAmount()));
        updateRunTotals(run);
        PayrollRun.PayrollAdjustment adjustment = new PayrollRun.PayrollAdjustment();
        adjustment.setEmployeeId(employeeId);
        adjustment.setType(normalizedType);
        adjustment.setCode(line.getCode());
        adjustment.setName(line.getName());
        adjustment.setAmount(line.getAmount());
        adjustment.setReason(reason.trim());
        adjustment.setActor(defaultValue(normalize(actor), "system"));
        if (run.getAdjustments() == null) run.setAdjustments(new ArrayList<>());
        run.getAdjustments().add(adjustment);
        if (run.getInputSnapshot() == null) run.setInputSnapshot(new LinkedHashMap<>());
        run.getInputSnapshot().put("reviewAdjustments", run.getAdjustments().stream().map(value -> Map.of(
                "employeeId", value.getEmployeeId(),
                "type", value.getType(),
                "code", value.getCode(),
                "name", value.getName(),
                "amount", value.getAmount(),
                "reason", value.getReason(),
                "actor", value.getActor())).toList());
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "ONE_OFF_ADJUSTMENT", actor, reason.trim(), Map.of(
                "employeeId", employeeId,
                "type", normalizedType,
                "code", line.getCode(),
                "amount", line.getAmount()));
        return saved;
    }

    public PayrollRun reopenPayroll(String id, String reason, String actor) {
        PayrollRun source = payrollRun(id);
        requireText(reason, "A reason is required to reopen payroll");
        if (!Set.of("PROCESSED", "PAID").contains(source.getStatus())) {
            throw conflict("Only locked or paid payroll runs can be reopened");
        }
        PayrollRun reopened = new PayrollRun();
        reopened.setMonth(source.getMonth());
        reopened.setRunType(source.getRunType());
        reopened.setVersion(source.getVersion() + 1);
        reopened.setStatus("DRAFT");
        reopened.setCreatedBy(actor);
        reopened.setReopenedFrom(source.getId());
        reopened.setReopenReason(reason.trim());
        reopened.setInputSnapshot(Map.of(
                "month", source.getMonth(),
                "runType", source.getRunType(),
                "version", source.getVersion() + 1,
                "reopenedFrom", source.getId()));
        PayrollRun saved = mongo.save(reopened);
        auditPayroll(source, "REOPENED", actor, reason.trim(),
                Map.of("newRunId", saved.getId(), "newVersion", saved.getVersion()));
        auditPayroll(saved, "CREATED_FROM_REOPEN", actor, reason.trim(),
                Map.of("sourceRunId", source.getId(), "version", saved.getVersion()));
        return saved;
    }

    public List<PayrollAuditLog> payrollAudit(String id) {
        payrollRun(id);
        return mongo.find(Query.query(Criteria.where("entityId").is(id))
                .with(Sort.by(Sort.Direction.ASC, "createdAt")), PayrollAuditLog.class);
    }

    public PayrollRun requestPayrollApproval(String id, String requester) {
        PayrollRun run = payrollRun(id);
        if (!"CALCULATED".equals(run.getStatus())) {
            throw conflict("Only calculated payroll runs can be sent for approval");
        }
        run.setStatus("PENDING_APPROVAL");
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "SUBMITTED_FOR_APPROVAL", requester, null,
                Map.of("previousStatus", "CALCULATED"));
        return saved;
    }

    public PayrollRun approvePayroll(String id, String approver) {
        PayrollRun run = payrollRun(id);
        if (!"PENDING_APPROVAL".equals(run.getStatus())) {
            throw conflict("Only payroll runs submitted for approval can be approved");
        }
        if (hasText(run.getCreatedBy()) && run.getCreatedBy().equalsIgnoreCase(approver)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Payroll maker and approver must be different users");
        }
        String previousStatus = run.getStatus();
        run.setStatus("APPROVED");
        run.setApprovedBy(approver);
        run.setApprovedAt(Instant.now());
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "APPROVED", approver, null, Map.of("previousStatus", previousStatus));
        return saved;
    }

    public PayrollRun rejectPayroll(String id, String reason, String actor) {
        PayrollRun run = payrollRun(id);
        if (!Set.of("CALCULATED", "PENDING_APPROVAL").contains(run.getStatus())) {
            throw conflict("Only calculated payroll runs can be rejected");
        }
        run.setStatus("REJECTED");
        run.setRejectionReason(normalize(reason));
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "REJECTED", actor, run.getRejectionReason(), Map.of());
        return saved;
    }

    public PayrollRun resetRejectedPayroll(String id, String actor) {
        PayrollRun run = payrollRun(id);
        if (!"REJECTED".equals(run.getStatus())) throw conflict("Only rejected payroll runs can return to draft");
        run.setStatus("DRAFT");
        run.setRejectionReason(null);
        run.setItems(new ArrayList<>());
        run.setEmployeeCount(0);
        run.setTotalGross(zero());
        run.setTotalDeduction(zero());
        run.setTotalNet(zero());
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "RESET_TO_DRAFT", actor, null, Map.of());
        return saved;
    }

    public PayrollRun processPayroll(String id, String actor) {
        PayrollRun run = payrollRun(id);
        if (!"APPROVED".equals(run.getStatus())) throw conflict("Only approved payroll runs can be processed");
        List<Payslip> existing = mongo.find(Query.query(Criteria.where("payrollId").is(run.getId())), Payslip.class);
        if (!existing.isEmpty()) throw conflict("Payslips have already been generated for this payroll run");
        for (PayrollRun.PayrollItem item : run.getItems()) {
            if (item.isSalaryOnHold()) continue;
            Employee employee = require(Employee.class, item.getEmployeeId(), "Employee");
            Payslip payslip = new Payslip();
            payslip.setPayrollId(run.getId());
            payslip.setPayrollItemId(UUID.randomUUID().toString());
            payslip.setEmployeeId(employee.getId());
            payslip.setEmployeeCode(employee.getEmployeeCode());
            payslip.setEmployeeEmail(employee.getEmail());
            payslip.setEmployeeName(employee.getFullName());
            payslip.setEmployeeType(employee.getEmployeeType());
            payslip.setPanNumber(employee.getPanNumber());
            payslip.setAadhaarLastFour(employee.getAadhaarLastFour());
            payslip.setPfUanNumber(employee.getPfUanNumber());
            payslip.setJoiningDate(employee.getJoiningDate());
            payslip.setDepartmentName(employeeDepartmentName(employee));
            payslip.setDesignationName(designationName(employee.getDesignationId()));
            payslip.setWorkLocation(employee.getLocation());
            payslip.setMonth(run.getMonth());
            payslip.setDaysInMonth(parseMonth(run.getMonth()).lengthOfMonth());
            payslip.setPaidDays(item.getPaidDays());
            payslip.setUnpaidDays(item.getUnpaidDays());
            payslip.setWeeklyOffDays(item.getWeeklyOffDays());
            payslip.setOvertimeHours(item.getOvertimeHours());
            payslip.setGrossSalary(item.getGrossSalary());
            payslip.setTotalDeduction(item.getTotalDeduction());
            payslip.setNetSalary(item.getNetSalary());
            payslip.setEarnings(item.getEarnings());
            payslip.setDeductions(item.getDeductions());
            payslip.setGeneratedDate(Instant.now());
            mongo.save(payslip);
        }
        run.setStatus("PROCESSED");
        run.setProcessedAt(Instant.now());
        run.setLockedAt(run.getProcessedAt());
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "LOCKED", actor, null, Map.of("payslipCount",
                run.getItems().stream().filter(item -> !item.isSalaryOnHold()).count()));
        return saved;
    }

    public PayrollRun markPayrollPaid(String id, String actor) {
        PayrollRun run = payrollRun(id);
        if (!"PROCESSED".equals(run.getStatus())) throw conflict("Only processed payroll runs can be marked paid");
        run.setStatus("PAID");
        Instant paidAt = Instant.now();
        run.setPaidAt(paidAt);
        List<Payslip> generatedPayslips = mongo.find(
                Query.query(Criteria.where("payrollId").is(run.getId())), Payslip.class);
        for (Payslip payslip : generatedPayslips) {
            payslip.setPaidAt(paidAt);
            mongo.save(payslip);
        }
        PayrollRun saved = mongo.save(run);
        auditPayroll(saved, "PAID", actor, null, Map.of());
        return saved;
    }

    public List<Payslip> payslips(String monthValue) {
        if (monthValue == null || monthValue.isBlank()) return mongo.findAll(Payslip.class);
        String month = parseMonth(monthValue).toString();
        return mongo.find(Query.query(Criteria.where("month").is(month)), Payslip.class);
    }

    public List<Payslip> myPayslips(String email) {
        return mongo.find(Query.query(Criteria.where("employeeEmail").is(normalizeEmail(email))), Payslip.class);
    }

    public List<Payslip> payslipsForEmployeeMonth(String employeeId, String monthValue) {
        require(Employee.class, employeeId, "Employee");
        String month = parseMonth(monthValue).toString();
        return mongo.find(Query.query(Criteria.where("employeeId").is(employeeId).and("month").is(month)), Payslip.class);
    }

    public boolean ownsEmployee(String employeeId, String email) {
        Employee employee = mongo.findById(employeeId, Employee.class);
        return employee != null && employee.getEmail() != null
                && employee.getEmail().equalsIgnoreCase(normalizeEmail(email));
    }

    public Payslip payslip(String id) {
        return require(Payslip.class, id, "Payslip");
    }

    public boolean ownsPayslip(Payslip payslip, String email) {
        return payslip.getEmployeeEmail() != null
                && payslip.getEmployeeEmail().equalsIgnoreCase(normalizeEmail(email));
    }

    public byte[] payslipPdf(Payslip payslip) {
        Instant paidAt = payslip.getPaidAt();
        if (paidAt == null && mongo != null && hasText(payslip.getPayrollId())) {
            List<PayrollAuditLog> paidEvents = mongo.find(
                    Query.query(Criteria.where("entityId").is(payslip.getPayrollId())
                                    .and("action").is("PAID"))
                            .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(1),
                    PayrollAuditLog.class);
            if (!paidEvents.isEmpty()) paidAt = paidEvents.get(0).getCreatedAt();
        }
        return createPdf(List.of(payslipPdfPage(payslip, paidAt)));
    }

    public Map<String, Object> reports(String monthValue) {
        YearMonth month = parseMonth(monthValue);
        List<PayrollRun> runs = payrollRuns(month.toString());
        List<Attendance> attendance = mongo.find(Query.query(Criteria.where("attendanceDate")
                .gte(month.atDay(1)).lte(month.atEndOfMonth())), Attendance.class);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("employeeCount", mongo.findAll(Employee.class).size());
        report.put("attendanceCount", attendance.size());
        report.put("payrollRunCount", runs.size());
        report.put("totalNet", runs.stream().map(PayrollRun::getTotalNet).filter(value -> value != null)
                .reduce(zero(), BigDecimal::add));
        report.put("month", month.toString());
        return report;
    }

    public String reportCsv(String monthValue) {
        YearMonth month = parseMonth(monthValue);
        List<Employee> employees = mongo.findAll(Employee.class);
        StringBuilder csv = new StringBuilder("employee_code,employee_name,employee_type,department,status,joining_date\n");
        for (Employee employee : employees) {
            csv.append(csvCell(employee.getEmployeeCode())).append(',')
                    .append(csvCell(employee.getFullName())).append(',')
                    .append(csvCell(employee.getEmployeeType())).append(',')
                    .append(csvCell(employeeDepartmentName(employee))).append(',')
                    .append(csvCell(employee.getStatus())).append(',')
                    .append(csvCell(employee.getJoiningDate() == null ? "" : employee.getJoiningDate().toString()))
                    .append('\n');
        }
        csv.append("\nPayroll month,").append(month).append('\n');
        for (PayrollRun run : payrollRuns(month.toString())) {
            csv.append("Gross,").append(run.getTotalGross()).append('\n')
                    .append("Deductions,").append(run.getTotalDeduction()).append('\n')
                    .append("Net,").append(run.getTotalNet()).append('\n');
        }
        return csv.toString();
    }

    PayrollRun.PayrollItem calculateEmployee(
            Employee employee, List<SalaryStructureTimeline.Segment> segments, YearMonth month) {
        PayrollRun.PayrollItem item = new PayrollRun.PayrollItem();
        item.setEmployeeId(employee.getId());
        item.setEmployeeCode(employee.getEmployeeCode());
        item.setEmployeeName(employee.getFullName());
        item.setEmployeeType(employee.getEmployeeType());
        item.setDepartmentName(employeeDepartmentName(employee));

        Map<String, PayrollRun.ComponentAmount> earnings = new LinkedHashMap<>();
        Map<String, PayrollRun.ComponentAmount> deductions = new LinkedHashMap<>();
        BigDecimal gross = zero();
        BigDecimal totalDeductions = zero();
        BigDecimal paidDays = zero();
        BigDecimal unpaidDays = zero();
        BigDecimal overtimeHours = zero();
        BigDecimal weeklyOffDays = zero();
        int workingDays = 0;
        for (SalaryStructureTimeline.Segment segment : segments) {
            SalaryStructure structure = segment.structure();
            MonthlyAttendance monthlyAttendance = monthlyAttendance(employee, segment.from(), segment.to());
            List<SalaryComponent> components = structure.getComponents().stream()
                    .map(line -> require(SalaryComponent.class, line.getComponentId(), "Salary component"))
                    .sorted(Comparator.comparing((SalaryComponent component) -> !"BASIC".equals(component.getCode()))
                            .thenComparing(SalaryComponent::getCode))
                    .toList();
            if (components.stream().anyMatch(component -> !"ACTIVE".equals(component.getStatus()))) {
                throw badRequest("Salary structure includes an inactive component");
            }
            Map<String, BigDecimal> calculated = new LinkedHashMap<>();
            BigDecimal monthlyGross = zero();
            for (SalaryComponent component : components) {
                if (!"EARNING".equals(component.getType())) continue;
                BigDecimal monthlyAmount = componentAmount(component, lineFor(structure, component.getId()),
                        calculated, monthlyGross, monthlyAttendance);
                BigDecimal amount = PayrollCalculator.prorate(
                        monthlyAmount, monthlyAttendance.paidDays(), month.lengthOfMonth());
                item.getTrace().add(Map.of(
                        "componentCode", component.getCode(),
                        "componentName", component.getName(),
                        "type", "EARNING",
                        "calculationType", component.getCalculationType(),
                        "monthlyAmount", monthlyAmount,
                        "paidDays", monthlyAttendance.paidDays(),
                        "workingDaysInMonth", month.lengthOfMonth(),
                        "calculatedAmount", round(amount)));
                calculated.put(component.getCode(), monthlyAmount);
                monthlyGross = monthlyGross.add(monthlyAmount);
                gross = gross.add(amount);
                mergeComponentAmount(earnings, component, amount);
            }
            calculated.put("GROSS", monthlyGross);
            for (SalaryComponent component : components) {
                if (!"DEDUCTION".equals(component.getType())) continue;
                BigDecimal monthlyAmount = componentAmount(component, lineFor(structure, component.getId()),
                        calculated, monthlyGross, monthlyAttendance);
                BigDecimal amount = PayrollCalculator.prorate(
                        monthlyAmount, monthlyAttendance.paidDays(), month.lengthOfMonth());
                item.getTrace().add(Map.of(
                        "componentCode", component.getCode(),
                        "componentName", component.getName(),
                        "type", "DEDUCTION",
                        "calculationType", component.getCalculationType(),
                        "monthlyAmount", monthlyAmount,
                        "paidDays", monthlyAttendance.paidDays(),
                        "workingDaysInMonth", month.lengthOfMonth(),
                        "calculatedAmount", round(amount)));
                totalDeductions = totalDeductions.add(amount);
                mergeComponentAmount(deductions, component, amount);
            }

            workingDays += monthlyAttendance.workingDays();
            paidDays = paidDays.add(monthlyAttendance.paidDays());
            unpaidDays = unpaidDays.add(BigDecimal.valueOf(monthlyAttendance.workingDays())
                    .subtract(monthlyAttendance.paidDays()));
            overtimeHours = overtimeHours.add(monthlyAttendance.overtimeHours());
            weeklyOffDays = weeklyOffDays.add(monthlyAttendance.weeklyOffDays());
        }

        item.setGrossSalary(round(gross));
        item.setTotalDeduction(round(totalDeductions));
        item.setNetSalary(round(gross.subtract(totalDeductions)));
        item.setWorkingDays(workingDays);
        item.setPaidDays(paidDays);
        item.setUnpaidDays(unpaidDays);
        item.setWeeklyOffDays(weeklyOffDays);
        item.setOvertimeHours(overtimeHours);
        item.setEarnings(new ArrayList<>(earnings.values()));
        item.setDeductions(new ArrayList<>(deductions.values()));
        return item;
    }

    private BigDecimal componentAmount(
            SalaryComponent component,
            SalaryStructure.ComponentLine line,
            Map<String, BigDecimal> amounts,
            BigDecimal gross,
            MonthlyAttendance attendance) {
        BigDecimal base = amounts.getOrDefault(component.getBaseCode(), zero());
        if ("PERCENTAGE".equals(component.getCalculationType()) && !amounts.containsKey(component.getBaseCode())) {
            throw badRequest("Salary component " + component.getCode()
                    + " references a base component that has not been calculated: " + component.getBaseCode());
        }
        Map<String, BigDecimal> variables = formulaVariables(
                amounts.getOrDefault("BASIC", zero()), gross, attendance.overtimeHours(),
                line.getUnits() == null ? BigDecimal.ONE : line.getUnits(),
                attendance.workingDays(), attendance.paidDays());
        BigDecimal amount;
        switch (component.getCalculationType()) {
            case "FIXED" -> amount = line.getAmount() != null && line.getAmount().signum() > 0
                    ? line.getAmount() : component.getValue();
            case "PERCENTAGE" -> amount = base.multiply(component.getValue())
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            case "FORMULA" -> amount = PayrollFormula.evaluate(component.getFormula(), variables);
            default -> throw badRequest("Unsupported calculation type: " + component.getCalculationType());
        }
        if (amount.signum() < 0) throw badRequest("Salary component " + component.getCode() + " calculated a negative amount");
        return round(amount);
    }

    private MonthlyAttendance monthlyAttendance(Employee employee, LocalDate start, LocalDate end) {
        int workingDays = Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1);
        Query attendanceQuery = Query.query(Criteria.where("employeeId").is(employee.getId())
                .and("attendanceDate").gte(start).lte(end));
        List<Attendance> monthAttendance = mongo.find(attendanceQuery, Attendance.class);
        Map<LocalDate, BigDecimal> unpaid = new HashMap<>();
        BigDecimal overtime = zero();
        BigDecimal weeklyOffDays = zero();
        for (Attendance record : monthAttendance) {
            if (record.getAttendanceDate() == null) continue;
            if ("ABSENT".equals(record.getStatus())) unpaid.put(record.getAttendanceDate(), BigDecimal.ONE);
            else if ("HALF_DAY".equals(record.getStatus())) unpaid.put(record.getAttendanceDate(), new BigDecimal("0.5"));
            else if ("WEEK_OFF".equals(record.getStatus())) weeklyOffDays = weeklyOffDays.add(BigDecimal.ONE);
            overtime = overtime.add(nonNegative(record.getOvertimeHours()));
        }

        List<LeaveRequest> leaves = mongo.find(Query.query(Criteria.where("employeeId").is(employee.getId())
                .and("status").is("APPROVED").and("leaveType").is("UNPAID")
                .and("fromDate").lte(end).and("toDate").gte(start)), LeaveRequest.class);
        for (LeaveRequest leave : leaves) {
            LocalDate first = leave.getFromDate().isBefore(start) ? start : leave.getFromDate();
            LocalDate last = leave.getToDate().isAfter(end) ? end : leave.getToDate();
            for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                unpaid.merge(day, BigDecimal.ONE, BigDecimal::max);
            }
        }
        BigDecimal unpaidDays = unpaid.values().stream().reduce(zero(), BigDecimal::add)
                .min(BigDecimal.valueOf(workingDays));
        return new MonthlyAttendance(
                workingDays, BigDecimal.valueOf(workingDays).subtract(unpaidDays), overtime, weeklyOffDays);
    }

    private Map<String, BigDecimal> approvedOvertimeAllowances(String month) {
        Map<String, BigDecimal> allowances = new HashMap<>();
        mongo.find(Query.query(Criteria.where("month").is(month).and("status").is("APPROVED")),
                        OvertimeAllowanceRequest.class)
                .forEach(request -> allowances.merge(
                        request.getEmployeeId(), request.getApprovedAmount(), BigDecimal::add));
        return allowances;
    }

    static void applyOvertimeAllowance(PayrollRun.PayrollItem item, BigDecimal allowance) {
        if (allowance == null || allowance.signum() <= 0) return;
        BigDecimal approvedAmount = round(allowance);
        PayrollRun.ComponentAmount line = new PayrollRun.ComponentAmount();
        line.setCode("OVERTIME_ALLOWANCE");
        line.setName("Approved overtime allowance");
        line.setAmount(approvedAmount);
        item.getEarnings().add(line);
        item.setGrossSalary(round(item.getGrossSalary().add(approvedAmount)));
        item.setNetSalary(round(item.getNetSalary().add(approvedAmount)));
        item.getTrace().add(Map.of(
                "componentCode", "OVERTIME_ALLOWANCE",
                "componentName", "Approved overtime allowance",
                "type", "EARNING",
                "source", "APPROVED_OVERTIME",
                "calculatedAmount", approvedAmount));
    }

    private Map<String, Object> overtimeAllowanceView(OvertimeAllowanceRequest request) {
        Map<String, Object> row = beanMap(request);
        row.put("employeeName", request.getEmployeeName());
        row.put("employeeCode", request.getEmployeeCode());
        return row;
    }

    private List<SalaryStructure> salaryStructuresForMonth(String employeeId, YearMonth month) {
        return mongo.find(Query.query(Criteria.where("employeeId").is(employeeId)
                        .and("status").is("ACTIVE").and("effectiveFrom").lte(month.atEndOfMonth())
                        .orOperator(Criteria.where("effectiveTo").gte(month.atDay(1)),
                                Criteria.where("effectiveTo").is(null))),
                SalaryStructure.class).stream()
                .sorted(Comparator.comparing(SalaryStructure::getEffectiveFrom)).toList();
    }

    private PayrollRun payrollForMonth(String month) {
        return mongo.findOne(Query.query(Criteria.where("month").is(month))
                .with(Sort.by(Sort.Direction.DESC, "version")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))), PayrollRun.class);
    }

    private PayrollRun.PayrollItem payrollItem(PayrollRun run, String employeeId) {
        if (!hasText(employeeId)) throw badRequest("Employee ID is required");
        return run.getItems().stream()
                .filter(item -> employeeId.equals(item.getEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee is not included in this payroll run"));
    }

    private void updateRunTotals(PayrollRun run) {
        run.setEmployeeCount(run.getItems().size());
        run.setTotalGross(sum(run.getItems(), PayrollRun.PayrollItem::getGrossSalary));
        run.setTotalDeduction(sum(run.getItems(), PayrollRun.PayrollItem::getTotalDeduction));
        run.setTotalNet(sum(run.getItems(), PayrollRun.PayrollItem::getNetSalary));
    }

    private void applyPayrollFlags(List<PayrollRun.PayrollItem> items, PayrollRun previousRun) {
        Map<String, PayrollRun.PayrollItem> previousItems = new HashMap<>();
        if (previousRun != null && previousRun.getItems() != null) {
            previousRun.getItems().forEach(item -> previousItems.put(item.getEmployeeId(), item));
        }
        for (PayrollRun.PayrollItem item : items) {
            if (item.getNetSalary().signum() < 0) item.getFlags().add("NEGATIVE_NET");
            if (item.getGrossSalary().signum() == 0) item.getFlags().add("ZERO_SALARY");
            PayrollRun.PayrollItem prior = previousItems.get(item.getEmployeeId());
            if (prior != null && prior.getGrossSalary() != null && prior.getGrossSalary().signum() > 0) {
                BigDecimal variance = item.getGrossSalary().subtract(prior.getGrossSalary()).abs()
                        .divide(prior.getGrossSalary(), 8, RoundingMode.HALF_UP);
                if (variance.compareTo(new BigDecimal("0.15")) > 0) item.getFlags().add("GROSS_VARIANCE_OVER_15_PERCENT");
            }
        }
    }

    private void auditPayroll(
            PayrollRun run, String action, String actor, String reason, Map<String, Object> details) {
        PayrollAuditLog event = new PayrollAuditLog();
        event.setEntityId(run.getId());
        event.setAction(action);
        event.setActor(defaultValue(normalize(actor), "system"));
        event.setReason(normalize(reason));
        event.setDetails(new LinkedHashMap<>(details));
        event.setCreatedAt(Instant.now());
        mongo.save(event);
    }

    private List<Employee> payrollEligibleEmployees() {
        return mongo.find(Query.query(Criteria.where("status").in("ACTIVE", "ON_LEAVE", "NOTICE_PERIOD")), Employee.class);
    }

    private Employee employeeForEmail(String email) {
        if (!hasText(email)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A signed-in employee account is required");
        Employee employee = mongo.findOne(Query.query(Criteria.where("email").is(normalizeEmail(email))), Employee.class);
        if (employee == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Your account is not linked to an employee record");
        return employee;
    }

    private void validateEmployee(Employee employee, String excludedId) {
        requireText(employee.getFirstName(), "First name is required");
        requireText(employee.getLastName(), "Last name is required");
        requireText(employee.getMobile(), "Mobile number is required");
        requireText(employee.getEmployeeType(), "Employee type is required");
        boolean configuredType = employeeTypes().stream().anyMatch(type ->
                "ACTIVE".equals(type.getStatus()) && upper(employee.getEmployeeType()).equals(type.getCode()));
        if (!configuredType) throw badRequest("Employee type is not configured or active");
        if (employee.getJoiningDate() == null) throw badRequest("Joining date is required");
        String status = defaultValue(upper(employee.getStatus()), "ONBOARDING");
        if (!EMPLOYEE_STATUSES.contains(status)) throw badRequest("Unsupported employee status");
        String employmentType = defaultValue(upper(employee.getEmploymentType()), "FULL_TIME");
        if (!EMPLOYMENT_TYPES.contains(employmentType)) throw badRequest("Unsupported employment type");
        Department department = hasText(employee.getDepartmentId())
                ? require(Department.class, employee.getDepartmentId(), "Department") : null;
        if (department != null && !"ACTIVE".equals(department.getStatus())) throw badRequest("Employee department is inactive");
        if (hasText(employee.getDesignationId())) {
            Designation designation = require(Designation.class, employee.getDesignationId(), "Designation");
            if (!"ACTIVE".equals(designation.getStatus())) throw badRequest("Employee designation is inactive");
            if (department != null && hasText(designation.getDepartmentId())
                    && !department.getId().equals(designation.getDepartmentId())) {
                throw badRequest("Designation does not belong to the selected department");
            }
        }
        if (hasText(employee.getManagerId())) {
            if (employee.getManagerId().equals(excludedId)) throw badRequest("An employee cannot report to themselves");
            Employee manager = require(Employee.class, employee.getManagerId(), "Reporting manager");
            if ("TERMINATED".equals(manager.getStatus())) throw badRequest("Reporting manager is terminated");
        }
        if (hasText(employee.getShiftId())) {
            Shift shift = require(Shift.class, employee.getShiftId(), "Shift");
            if (!"ACTIVE".equals(shift.getStatus())) throw badRequest("Employee shift is inactive");
        }
        if (hasText(employee.getDoctorProfileId())) {
            if (!"DOCTOR".equals(upper(employee.getEmployeeType()))) {
                throw badRequest("A doctor profile can only be linked to a doctor employee");
            }
            if (mongo.findById(employee.getDoctorProfileId(), Doctor.class) == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile was not found");
            }
        }
        if (hasText(employee.getEmail())) {
            String email = normalizeEmail(employee.getEmail());
            Employee duplicate = mongo.findOne(Query.query(Criteria.where("email").is(email)), Employee.class);
            if (duplicate != null && !duplicate.getId().equals(excludedId)) throw conflict("An employee already uses this email");
        }
        if (hasText(employee.getEmployeeCode())) {
            String code = employee.getEmployeeCode().trim();
            Employee duplicate = mongo.findOne(Query.query(Criteria.where("employeeCode").is(code)), Employee.class);
            if (duplicate != null && !duplicate.getId().equals(excludedId)) throw conflict("Employee ID already exists");
        }
        String panNumber = normalize(employee.getPanNumber());
        if (panNumber != null && !panNumber.toUpperCase(Locale.ROOT).matches("[A-Z]{5}[0-9]{4}[A-Z]")) {
            throw badRequest("PAN must contain 10 characters in the format ABCDE1234F");
        }
        String aadhaarLastFour = normalize(employee.getAadhaarLastFour());
        if (aadhaarLastFour != null && !aadhaarLastFour.matches("[0-9]{4}")) {
            throw badRequest("Enter only the last four Aadhaar digits");
        }
        if (employee.getPfUanNumber() != null && employee.getPfUanNumber().length() > 30) {
            throw badRequest("PF / UAN number cannot exceed 30 characters");
        }
    }

    private void ensureUniqueCode(Class<?> type, String code, String id, String label) {
        boolean duplicate = mongo.findAll(type).stream().filter(item -> item instanceof Department
                        || item instanceof Designation || item instanceof SalaryComponent || item instanceof EmployeeType)
                .anyMatch(item -> {
                    String existingId;
                    String existingCode;
                    if (item instanceof Department department) {
                        existingId = department.getId();
                        existingCode = department.getCode();
                    } else if (item instanceof Designation designation) {
                        existingId = designation.getId();
                        existingCode = designation.getCode();
                    } else if (item instanceof SalaryComponent component) {
                        existingId = component.getId();
                        existingCode = component.getCode();
                    } else {
                        EmployeeType employeeType = (EmployeeType) item;
                        existingId = employeeType.getId();
                        existingCode = employeeType.getCode();
                    }
                    return !existingId.equals(id) && code.equalsIgnoreCase(existingCode);
                });
        if (duplicate) throw conflict(label + " code already exists");
    }

    private PayrollRun.ComponentAmount componentAmountView(SalaryComponent component, BigDecimal amount) {
        PayrollRun.ComponentAmount result = new PayrollRun.ComponentAmount();
        result.setCode(component.getCode());
        result.setName(component.getName());
        result.setAmount(round(amount));
        return result;
    }

    private void mergeComponentAmount(
            Map<String, PayrollRun.ComponentAmount> amounts, SalaryComponent component, BigDecimal amount) {
        PayrollRun.ComponentAmount existing = amounts.get(component.getCode());
        if (existing == null) {
            amounts.put(component.getCode(), componentAmountView(component, amount));
        } else {
            existing.setAmount(round(existing.getAmount().add(amount)));
        }
    }

    private SalaryStructure.ComponentLine lineFor(SalaryStructure structure, String componentId) {
        return structure.getComponents().stream().filter(line -> componentId.equals(line.getComponentId()))
                .findFirst().orElseThrow(() -> badRequest("Salary structure is missing component " + componentId));
    }

    private Map<String, Object> employeeView(Employee employee) {
        Map<String, Object> row = beanMap(employee);
        row.remove("aadhaarNumber");
        row.put("hasAadhaarNumber", hasText(employee.getAadhaarLastFour()));
        row.put("name", employee.getFullName());
        row.put("departmentName", employeeDepartmentName(employee));
        row.put("designationName", designationName(employee.getDesignationId()));
        Employee manager = employee.getManagerId() == null ? null
                : mongo.findById(employee.getManagerId(), Employee.class);
        row.put("managerName", manager == null ? null : manager.getFullName());
        Doctor doctorProfile = employee.getDoctorProfileId() == null ? null
                : mongo.findById(employee.getDoctorProfileId(), Doctor.class);
        row.put("doctorProfileName", doctorProfile == null ? null : doctorProfile.getDoctorName());
        row.put("doctorSpecialty", doctorProfile == null ? null : doctorProfile.getDoctorSpecialistName());
        return row;
    }

    private Map<String, Object> attendanceView(Attendance record) {
        Map<String, Object> row = beanMap(record);
        Employee employee = mongo.findById(record.getEmployeeId(), Employee.class);
        row.put("employeeName", employee == null ? null : employee.getFullName());
        row.put("employeeType", employee == null ? null : employee.getEmployeeType());
        return row;
    }

    private Map<String, Object> leaveView(LeaveRequest leave) {
        Map<String, Object> row = beanMap(leave);
        Employee employee = mongo.findById(leave.getEmployeeId(), Employee.class);
        row.put("employeeName", employee == null ? null : employee.getFullName());
        row.put("employeeCode", employee == null ? null : employee.getEmployeeCode());
        return row;
    }

    private Map<String, Object> structureView(SalaryStructure structure) {
        Map<String, Object> row = beanMap(structure);
        Employee employee = mongo.findById(structure.getEmployeeId(), Employee.class);
        row.put("employeeName", employee == null ? null : employee.getFullName());
        row.put("employeeType", employee == null ? null : employee.getEmployeeType());
        return row;
    }

    private String departmentName(String id) {
        if (!hasText(id)) return null;
        Department department = mongo.findById(id, Department.class);
        return department == null ? null : department.getName();
    }

    private String employeeDepartmentName(Employee employee) {
        String directoryName = departmentName(employee.getDepartmentId());
        return hasText(directoryName) ? directoryName : normalize(employee.getDepartmentName());
    }

    private String designationName(String id) {
        if (!hasText(id)) return null;
        Designation designation = mongo.findById(id, Designation.class);
        return designation == null ? null : designation.getName();
    }

    private <T> T require(Class<T> type, String id, String label) {
        if (!hasText(id)) throw badRequest(label + " ID is required");
        T result = mongo.findById(id, type);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " was not found");
        return result;
    }

    private static Map<String, Object> beanMap(Object bean) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            for (var descriptor : java.beans.Introspector.getBeanInfo(bean.getClass(), Object.class).getPropertyDescriptors()) {
                map.put(descriptor.getName(), descriptor.getReadMethod().invoke(bean));
            }
            return map;
        } catch (ReflectiveOperationException | java.beans.IntrospectionException exception) {
            throw new IllegalStateException("Could not serialize HR record", exception);
        }
    }

    private static Map<String, BigDecimal> formulaVariables(
            BigDecimal basic, BigDecimal gross, BigDecimal overtime, BigDecimal units,
            int workingDays, BigDecimal paidDays) {
        Map<String, BigDecimal> variables = new HashMap<>();
        variables.put("BASIC", basic);
        variables.put("GROSS", gross);
        variables.put("OVERTIME_HOURS", overtime);
        variables.put("UNITS", units);
        variables.put("WORKING_DAYS", BigDecimal.valueOf(workingDays));
        variables.put("PAID_DAYS", paidDays);
        return variables;
    }

    private static BigDecimal sum(List<PayrollRun.PayrollItem> items, Function<PayrollRun.PayrollItem, BigDecimal> amount) {
        return round(items.stream().map(amount).filter(value -> value != null).reduce(zero(), BigDecimal::add));
    }

    private static String csvCell(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String payslipPdfPage(Payslip payslip, Instant paidAt) {
        PdfCanvas canvas = new PdfCanvas();
        canvas.strokeRect(35, 35, 525, 772, DARK, 1.1f);
        canvas.text("MEDCARE HOSPITAL", 297.5f, 786, 17, DARK, true, PdfCanvas.Align.CENTER);
        canvas.text("Employer registration details not provided", 297.5f, 770, 8.5f, MUTED, false,
                PdfCanvas.Align.CENTER);
        String period = payslipMonthLabel(payslip.getMonth());
        canvas.text("PAYSLIP FOR THE MONTH OF " + period, 297.5f, 750, 12, TEAL, true, PdfCanvas.Align.CENTER);
        canvas.line(51, 740, 544, 740, 0.12, 0.17, 0.21, 0.8f);

        String uan = maskIdentifier(payslip.getPfUanNumber());
        payslipField(canvas, "Employee Code", payslip.getEmployeeCode(), 51, 727, 134, 150);
        payslipField(canvas, "Date of Joining", payslip.getJoiningDate() == null
                ? null : payslip.getJoiningDate().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)),
                303, 727, 389, 151);
        payslipField(canvas, "Employee Name", payslip.getEmployeeName(), 51, 709, 134, 150);
        payslipField(canvas, "Designation", payslip.getDesignationName(), 303, 709, 389, 151);
        payslipField(canvas, "Department", payslip.getDepartmentName(), 51, 691, 134, 150);
        payslipField(canvas, "Work Location", payslip.getWorkLocation(), 303, 691, 389, 151);
        payslipField(canvas, "PAN", payslip.getPanNumber(), 51, 673, 134, 150);
        payslipField(canvas, "UAN", uan, 303, 673, 389, 151);
        payslipField(canvas, "PF A/C No.", null, 51, 655, 134, 150);
        payslipField(canvas, "ESIC IP No.", null, 303, 655, 389, 151);
        payslipField(canvas, "Bank A/C No.", null, 51, 637, 134, 150);
        payslipField(canvas, "IFSC", null, 303, 637, 389, 151);

        float stripX = 51;
        float stripY = 604;
        float stripWidth = 493;
        canvas.fillRect(stripX, stripY, stripWidth, 26, PALE);
        String[] metricLabels = {"Days in month", "Paid days", "LOP days", "Weekly offs", "OT hours"};
        String[] metricValues = {
                payslip.getDaysInMonth() > 0 ? Integer.toString(payslip.getDaysInMonth()) : "Not recorded",
                decimalValue(payslip.getPaidDays(), payslip.getDaysInMonth() > 0),
                decimalValue(payslip.getUnpaidDays(), payslip.getDaysInMonth() > 0),
                decimalValue(payslip.getWeeklyOffDays(), payslip.getDaysInMonth() > 0),
                decimalValue(payslip.getOvertimeHours(), payslip.getDaysInMonth() > 0)
        };
        float metricWidth = stripWidth / metricLabels.length;
        for (int index = 0; index < metricLabels.length; index++) {
            float center = stripX + metricWidth * (index + 0.5f);
            canvas.text(metricLabels[index], center, stripY + 15, 7.5f, MUTED, false, PdfCanvas.Align.CENTER);
            canvas.text(metricValues[index], center, stripY + 4, 9.5f, DARK, true, PdfCanvas.Align.CENTER);
        }

        List<PayrollRun.ComponentAmount> earnings = safeComponents(payslip.getEarnings());
        List<PayrollRun.ComponentAmount> deductions = safeComponents(payslip.getDeductions());
        int rows = Math.max(7, Math.max(earnings.size(), deductions.size()));
        float rowHeight = Math.min(18, 162f / (rows + 1));
        float tableX = 51;
        float tableWidth = 493;
        float headerY = 576;
        canvas.fillRect(tableX, headerY, tableWidth, 23, TEAL);
        canvas.text("EARNINGS", tableX + 8, headerY + 7, 9.5f, WHITE, true, PdfCanvas.Align.LEFT);
        payslipAmountHeader(canvas, tableX + 244, headerY + 7);
        canvas.text("DEDUCTIONS", tableX + 264, headerY + 7, 9.5f, WHITE, true, PdfCanvas.Align.LEFT);
        payslipAmountHeader(canvas, tableX + tableWidth - 8, headerY + 7);
        float dividerX = tableX + 253;
        canvas.line(dividerX, headerY - 5, dividerX, headerY - (rows + 1) * rowHeight, 0.76, 0.84, 0.83, 0.8f);
        for (int index = 0; index < rows; index++) {
            float rowBottom = headerY - (index + 1) * rowHeight;
            if (index % 2 == 1) canvas.fillRect(tableX, rowBottom, tableWidth, rowHeight, 0.96, 0.97, 0.97);
            drawComponent(canvas, index < earnings.size() ? earnings.get(index) : null,
                    tableX + 8, tableX + 244, rowBottom + rowHeight / 2 - 3, rowHeight);
            drawComponent(canvas, index < deductions.size() ? deductions.get(index) : null,
                    tableX + 264, tableX + tableWidth - 8, rowBottom + rowHeight / 2 - 3, rowHeight);
        }
        float totalsY = headerY - (rows + 1) * rowHeight;
        canvas.fillRect(tableX, totalsY, tableWidth, rowHeight, PALE);
        canvas.text("GROSS EARNINGS", tableX + 8, totalsY + rowHeight / 2 - 3, 9.5f, DARK, true, PdfCanvas.Align.LEFT);
        canvas.text(formatMoney(payslip.getGrossSalary()), tableX + 244, totalsY + rowHeight / 2 - 3,
                9.5f, DARK, true, PdfCanvas.Align.RIGHT);
        canvas.text("TOTAL DEDUCTIONS", tableX + 264, totalsY + rowHeight / 2 - 3,
                9.5f, DARK, true, PdfCanvas.Align.LEFT);
        canvas.text(formatMoney(payslip.getTotalDeduction()), tableX + tableWidth - 8,
                totalsY + rowHeight / 2 - 3, 9.5f, DARK, true, PdfCanvas.Align.RIGHT);

        canvas.fillRect(51, 354, 493, 51, PALE_TEAL);
        canvas.strokeRect(51, 354, 493, 51, TEAL, 0.9f);
        canvas.text("NET PAY", 65, 384, 8.5f, MUTED, false, PdfCanvas.Align.LEFT);
        canvas.rupeeSymbol(65, 365, 0.7f, TEAL);
        canvas.text(formatMoney(payslip.getNetSalary()), 78, 365, 19, TEAL, true, PdfCanvas.Align.LEFT);
        canvas.text("In words", 530, 384, 8.5f, MUTED, false, PdfCanvas.Align.RIGHT);
        canvas.text(amountInWords(payslip.getNetSalary()), 530, 367, 8.2f, DARK, true, PdfCanvas.Align.RIGHT, 290);

        canvas.text("EMPLOYER CONTRIBUTIONS", 51, 333, 8.5f, TEAL, true, PdfCanvas.Align.LEFT);
        canvas.text("Not available from saved payroll data.", 51, 319, 9, DARK, false, PdfCanvas.Align.LEFT);
        canvas.text("LEAVE BALANCE", 51, 293, 8.5f, TEAL, true, PdfCanvas.Align.LEFT);
        canvas.text("YEAR-TO-DATE (APR " + payrollYear(payslip.getMonth())
                + " - " + payslipShortMonthLabel(payslip.getMonth()) + ")", 303, 293, 8.5f, TEAL, true,
                PdfCanvas.Align.LEFT, 241);
        canvas.text("Not available from saved leave-balance data.", 51, 278, 8.5f, DARK, false,
                PdfCanvas.Align.LEFT, 241);
        canvas.text("Not available from saved payroll data.", 303, 278, 8.5f, DARK, false,
                PdfCanvas.Align.LEFT, 241);
        canvas.text("Not available from saved payroll data.", 303, 263, 8.5f, DARK, false,
                PdfCanvas.Align.LEFT, 241);
        canvas.line(51, 237, 544, 237, 0.76, 0.84, 0.83, 0.8f);
        canvas.text("Payment mode: Not provided", 51, 221, 8.5f, MUTED, false, PdfCanvas.Align.LEFT);
        String paid = paidAt == null ? "Not recorded"
                : DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
                        .withZone(ZoneId.systemDefault()).format(paidAt);
        canvas.text("Paid on: " + paid, 544, 221, 8.5f, MUTED, false, PdfCanvas.Align.RIGHT);
        canvas.text("This is a computer-generated payslip and does not require a signature.",
                297.5f, 49, 8, MUTED, false, PdfCanvas.Align.CENTER);
        return canvas.content();
    }

    private static void payslipField(
            PdfCanvas canvas, String label, String value, float x, float y, float valueX, float maxValueWidth) {
        canvas.text(label, x, y, 8.5f, MUTED, false, PdfCanvas.Align.LEFT);
        canvas.text(": " + defaultValue(value, "Not provided"), valueX, y, 9, DARK, true,
                PdfCanvas.Align.LEFT, maxValueWidth);
    }

    private static void payslipAmountHeader(PdfCanvas canvas, float right, float y) {
        canvas.text("AMOUNT (", right - 23, y, 8.5f, WHITE, true, PdfCanvas.Align.RIGHT);
        canvas.rupeeSymbol(right - 20, y - 1, 0.28f, WHITE);
        canvas.text(")", right - 11, y, 8.5f, WHITE, true, PdfCanvas.Align.LEFT);
    }

    private static void drawComponent(
            PdfCanvas canvas, PayrollRun.ComponentAmount component, float x, float right, float y, float rowHeight) {
        if (component == null) return;
        float fontSize = Math.min(9.5f, Math.max(6, rowHeight * 0.53f));
        canvas.text(defaultValue(component.getName(), "Not provided"), x, y, fontSize, DARK, false,
                PdfCanvas.Align.LEFT, right - x - 72);
        canvas.text(formatMoney(component.getAmount()), right, y, fontSize, DARK, false, PdfCanvas.Align.RIGHT);
    }

    private static List<PayrollRun.ComponentAmount> safeComponents(List<PayrollRun.ComponentAmount> components) {
        return components == null ? List.of() : components;
    }

    private static String decimalValue(BigDecimal value, boolean available) {
        return available ? formatMoney(value).replace(".00", "") : "Not recorded";
    }

    private static String payslipMonthLabel(String month) {
        try {
            return YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                    .toUpperCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            return defaultValue(month, "NOT PROVIDED").toUpperCase(Locale.ROOT);
        }
    }

    private static String payrollYear(String month) {
        try {
            return YearMonth.parse(month).format(DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH));
        } catch (RuntimeException exception) {
            return "NOT PROVIDED";
        }
    }

    private static String payslipShortMonthLabel(String month) {
        try {
            return YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
                    .toUpperCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            return defaultValue(month, "NOT PROVIDED").toUpperCase(Locale.ROOT);
        }
    }

    private static String maskIdentifier(String value) {
        if (!hasText(value)) return "Not provided";
        String normalized = value.trim();
        return "XXXXXXXX" + normalized.substring(Math.max(0, normalized.length() - 4));
    }

    private static String formatMoney(BigDecimal amount) {
        DecimalFormat formatter = new DecimalFormat("#,##,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(amount == null ? zero() : amount);
    }

    private static String amountInWords(BigDecimal amount) {
        BigDecimal rounded = (amount == null ? zero() : amount).setScale(2, RoundingMode.HALF_UP);
        BigInteger rupees = rounded.abs().toBigInteger();
        int paise = rounded.abs().remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String words = indianNumberWords(rupees);
        if (rounded.signum() < 0) words = "Minus " + words;
        return "Rupees " + words + (paise == 0 ? " Only"
                : " and " + indianNumberWords(BigInteger.valueOf(paise)) + " Paise Only");
    }

    private static String indianNumberWords(BigInteger value) {
        if (value.signum() == 0) return "Zero";
        String[] units = {"", "Thousand", "Lakh", "Crore", "Arab", "Kharab"};
        BigInteger thousand = BigInteger.valueOf(1000);
        BigInteger hundred = BigInteger.valueOf(100);
        List<String> groups = new ArrayList<>();
        BigInteger remaining = value;
        groups.add(wordsBelowThousand(remaining.mod(thousand).intValue()));
        remaining = remaining.divide(thousand);
        int group = 1;
        while (remaining.signum() > 0) {
            int part = remaining.mod(BigInteger.valueOf(100)).intValue();
            if (part > 0) groups.add(wordsBelowThousand(part) + " "
                    + (group < units.length ? units[group] : "Crore"));
            remaining = remaining.divide(BigInteger.valueOf(100));
            group++;
        }
        List<String> words = new ArrayList<>();
        for (int index = groups.size() - 1; index >= 0; index--) {
            if (!groups.get(index).isBlank()) words.add(groups.get(index));
        }
        return String.join(" ", words);
    }

    private static String wordsBelowThousand(int value) {
        String[] ones = {"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
                "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
                "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        List<String> words = new ArrayList<>();
        if (value >= 100) {
            words.add(ones[value / 100]);
            words.add("Hundred");
            value %= 100;
        }
        if (value >= 20) {
            words.add(tens[value / 10]);
            value %= 10;
        }
        if (value > 0) words.add(ones[value]);
        return String.join(" ", words);
    }

    private static final double[] DARK = {0.10, 0.14, 0.18};
    private static final double[] TEAL = {0.05, 0.48, 0.44};
    private static final double[] PALE = {0.93, 0.96, 0.95};
    private static final double[] PALE_TEAL = {0.90, 0.96, 0.95};
    private static final double[] MUTED = {0.36, 0.42, 0.43};
    private static final double[] WHITE = {1, 1, 1};

    private static byte[] createPdf(List<String> pages) {
        try {
            List<byte[]> objects = new ArrayList<>();
            objects.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII));
            StringBuilder kids = new StringBuilder();
            for (int index = 0; index < pages.size(); index++) {
                int pageId = 5 + index * 2;
                kids.append(pageId).append(" 0 R ");
            }
            objects.add(("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>")
                    .getBytes(StandardCharsets.US_ASCII));
            objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".getBytes(StandardCharsets.US_ASCII));
            objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>".getBytes(StandardCharsets.US_ASCII));
            for (int index = 0; index < pages.size(); index++) {
                int pageId = 5 + index * 2;
                int streamId = pageId + 1;
                objects.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                        + streamId + " 0 R >>").getBytes(StandardCharsets.US_ASCII));
                byte[] stream = pages.get(index).getBytes(StandardCharsets.US_ASCII);
                objects.add(("<< /Length " + stream.length + " >>\nstream\n"
                        + pages.get(index) + "\nendstream").getBytes(StandardCharsets.US_ASCII));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            List<Integer> offsets = new ArrayList<>();
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(output.size());
                output.write((index + 1 + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
                output.write(objects.get(index));
                output.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            }
            int xref = output.size();
            output.write(("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n")
                    .getBytes(StandardCharsets.US_ASCII));
            for (int offset : offsets) {
                output.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
            }
            output.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                    + xref + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not generate payslip PDF", exception);
        }
    }

    private static final class PdfCanvas {
        private final StringBuilder commands = new StringBuilder();

        private enum Align { LEFT, RIGHT, CENTER }

        private void fillRect(float x, float y, float width, float height, double[] color) {
            fillRect(x, y, width, height, color[0], color[1], color[2]);
        }

        private void fillRect(float x, float y, float width, float height,
                double red, double green, double blue) {
            commands.append(String.format(Locale.ROOT, "%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f%n",
                    red, green, blue, x, y, width, height));
        }

        private void strokeRect(
                float x, float y, float width, float height, double[] color, float lineWidth) {
            commands.append(String.format(Locale.ROOT,
                    "%.3f %.3f %.3f RG %.2f w %.2f %.2f %.2f %.2f re S%n",
                    color[0], color[1], color[2], lineWidth, x, y, width, height));
        }

        private void line(float x1, float y1, float x2, float y2,
                double red, double green, double blue, float width) {
            commands.append(String.format(Locale.ROOT,
                    "%.3f %.3f %.3f RG %.2f w %.2f %.2f m %.2f %.2f l S%n",
                    red, green, blue, width, x1, y1, x2, y2));
        }

        private void rupeeSymbol(float x, float y, float scale, double[] color) {
            commands.append(String.format(Locale.ROOT,
                    "q 1 J 1 j %.3f %.3f %.3f RG %.2f w%n",
                    color[0], color[1], color[2], 1.15f * scale));
            commands.append(String.format(Locale.ROOT,
                    "%.2f %.2f m %.2f %.2f l "
                            + "%.2f %.2f m %.2f %.2f l "
                            + "%.2f %.2f m %.2f %.2f l "
                            + "%.2f %.2f m %.2f %.2f l "
                            + "%.2f %.2f %.2f %.2f %.2f %.2f c "
                            + "%.2f %.2f %.2f %.2f %.2f %.2f c%n",
                    x, y + 16 * scale, x + 14 * scale, y + 16 * scale,
                    x, y + 10 * scale, x + 11 * scale, y + 10 * scale,
                    x + 5 * scale, y + 16 * scale, x + 5 * scale, y + 10 * scale,
                    x + 5 * scale, y + 10 * scale, x + 9 * scale, y + 10 * scale,
                    x + 12 * scale, y + 10 * scale, x + 12 * scale, y + 7 * scale, x + 9 * scale, y + 7 * scale,
                    x + 7 * scale, y + 7 * scale, x + 8 * scale, y + 4 * scale, x + 12 * scale, y));
            commands.append("S Q\n");
        }

        private void text(String value, float x, float y, float size, double[] color, boolean bold, Align align) {
            text(value, x, y, size, color, bold, align, Float.MAX_VALUE);
        }

        private void text(
                String value, float x, float y, float size, double[] color, boolean bold, Align align, float maxWidth) {
            String safe = safePdfText(value);
            if (maxWidth < Float.MAX_VALUE) safe = fitPdfText(safe, maxWidth, size);
            float estimatedWidth = safe.length() * size * 0.52f;
            float textX = align == Align.RIGHT ? x - estimatedWidth
                    : align == Align.CENTER ? x - estimatedWidth / 2 : x;
            commands.append(String.format(Locale.ROOT, "BT /%s %.2f Tf %.3f %.3f %.3f rg %.2f %.2f Td (%s) Tj ET%n",
                    bold ? "F2" : "F1", size, color[0], color[1], color[2], textX, y,
                    safe.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")));
        }

        private String content() {
            return commands.toString();
        }
    }

    private static String safePdfText(String value) {
        if (value == null) return "";
        StringBuilder safe = new StringBuilder();
        for (char character : value.toCharArray()) {
            if (character >= 32 && character <= 126) safe.append(character);
            else if (character == '\u20ac') safe.append("EUR");
            else if (character == '\u20b9') safe.append("INR");
            else if (character == '\u2013' || character == '\u2014' || character == '\u2212') safe.append('-');
            else if (character == '\u00b7') safe.append(" | ");
            else safe.append('?');
        }
        return safe.toString();
    }

    private static String fitPdfText(String value, float width, float size) {
        int maxCharacters = Math.max(1, (int) (width / (size * 0.52f)));
        return value.length() <= maxCharacters ? value : value.substring(0, maxCharacters - 3) + "...";
    }

    private static String money(BigDecimal amount) {
        return "INR " + (amount == null ? zero() : amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static YearMonth parseMonth(String month) {
        try {
            if (month == null || month.isBlank()) return YearMonth.now();
            return YearMonth.parse(month);
        } catch (RuntimeException exception) {
            throw badRequest("Payroll month must use YYYY-MM format");
        }
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal normalized = value == null ? zero() : value;
        if (normalized.signum() < 0) throw badRequest("Amounts and hours cannot be negative");
        return normalized;
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validateActiveStatus(String status) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw badRequest("Status must be ACTIVE or INACTIVE");
    }

    private static int countStatus(List<Attendance> attendance, String status) {
        return (int) attendance.stream().filter(item -> status.equals(item.getStatus())).count();
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void normalizeStatutoryIdentifiers(Employee employee) {
        employee.setPanNumber(upper(normalize(employee.getPanNumber())));
        employee.setAadhaarLastFour(normalize(employee.getAadhaarLastFour()));
        employee.setPfUanNumber(upper(normalize(employee.getPfUanNumber())));
    }

    private static String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireText(String value, String message) {
        if (!hasText(value)) throw badRequest(message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record MonthlyAttendance(
            int workingDays, BigDecimal paidDays, BigDecimal overtimeHours, BigDecimal weeklyOffDays) {}
}
