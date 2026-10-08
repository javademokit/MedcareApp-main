package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.hrpayroll.Attendance;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.hrpayroll.Department;
import com.example.MedcareApp.Entity.hrpayroll.Designation;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.hrpayroll.EmployeeType;
import com.example.MedcareApp.Entity.hrpayroll.LeaveRequest;
import com.example.MedcareApp.Entity.hrpayroll.OvertimeAllowanceRequest;
import com.example.MedcareApp.Entity.hrpayroll.PayrollRun;
import com.example.MedcareApp.Entity.hrpayroll.Payslip;
import com.example.MedcareApp.Entity.hrpayroll.SalaryComponent;
import com.example.MedcareApp.Entity.hrpayroll.SalaryStructure;
import com.example.MedcareApp.Entity.hrpayroll.Shift;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
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
        if (monthValue == null || monthValue.isBlank()) return mongo.findAll(PayrollRun.class);
        String month = parseMonth(monthValue).toString();
        return mongo.find(Query.query(Criteria.where("month").is(month)), PayrollRun.class);
    }

    public PayrollRun payrollRun(String id) {
        return require(PayrollRun.class, id, "Payroll run");
    }

    public PayrollRun createPayrollRun(String monthValue, String actor) {
        String month = parseMonth(monthValue).toString();
        if (payrollForMonth(month) != null) throw conflict("A payroll run already exists for " + month);
        PayrollRun run = new PayrollRun();
        run.setMonth(month);
        run.setStatus("DRAFT");
        run.setCreatedBy(actor);
        return mongo.save(run);
    }

    public PayrollRun calculatePayroll(String id) {
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
        run.setItems(items);
        run.setEmployeeCount(items.size());
        run.setTotalGross(sum(items, PayrollRun.PayrollItem::getGrossSalary));
        run.setTotalDeduction(sum(items, PayrollRun.PayrollItem::getTotalDeduction));
        run.setTotalNet(sum(items, PayrollRun.PayrollItem::getNetSalary));
        run.setStatus("CALCULATED");
        return mongo.save(run);
    }

    public PayrollRun approvePayroll(String id, String approver) {
        PayrollRun run = payrollRun(id);
        if (!Set.of("CALCULATED", "PENDING_APPROVAL").contains(run.getStatus())) {
            throw conflict("Only calculated payroll runs can be approved");
        }
        run.setStatus("APPROVED");
        run.setApprovedBy(approver);
        run.setApprovedAt(Instant.now());
        return mongo.save(run);
    }

    public PayrollRun rejectPayroll(String id, String reason) {
        PayrollRun run = payrollRun(id);
        if (!Set.of("CALCULATED", "PENDING_APPROVAL").contains(run.getStatus())) {
            throw conflict("Only calculated payroll runs can be rejected");
        }
        run.setStatus("REJECTED");
        run.setRejectionReason(normalize(reason));
        return mongo.save(run);
    }

    public PayrollRun resetRejectedPayroll(String id) {
        PayrollRun run = payrollRun(id);
        if (!"REJECTED".equals(run.getStatus())) throw conflict("Only rejected payroll runs can return to draft");
        run.setStatus("DRAFT");
        run.setRejectionReason(null);
        run.setItems(new ArrayList<>());
        run.setEmployeeCount(0);
        run.setTotalGross(zero());
        run.setTotalDeduction(zero());
        run.setTotalNet(zero());
        return mongo.save(run);
    }

    public PayrollRun processPayroll(String id) {
        PayrollRun run = payrollRun(id);
        if (!"APPROVED".equals(run.getStatus())) throw conflict("Only approved payroll runs can be processed");
        List<Payslip> existing = mongo.find(Query.query(Criteria.where("payrollId").is(run.getId())), Payslip.class);
        if (!existing.isEmpty()) throw conflict("Payslips have already been generated for this payroll run");
        for (PayrollRun.PayrollItem item : run.getItems()) {
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
            payslip.setDepartmentName(employeeDepartmentName(employee));
            payslip.setDesignationName(designationName(employee.getDesignationId()));
            payslip.setMonth(run.getMonth());
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
        return mongo.save(run);
    }

    public PayrollRun markPayrollPaid(String id) {
        PayrollRun run = payrollRun(id);
        if (!"PROCESSED".equals(run.getStatus())) throw conflict("Only processed payroll runs can be marked paid");
        run.setStatus("PAID");
        return mongo.save(run);
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
        List<String> lines = new ArrayList<>();
        lines.add("EMPLOYEE: " + payslip.getEmployeeName() + "  (" + payslip.getEmployeeCode() + ")");
        lines.add("PAN: " + defaultValue(payslip.getPanNumber(), "Not provided"));
        lines.add("Aadhaar: " + (hasText(payslip.getAadhaarLastFour())
                ? "XXXX XXXX " + payslip.getAadhaarLastFour() : "Not provided"));
        lines.add("PF / UAN: " + defaultValue(payslip.getPfUanNumber(), "Not provided"));
        lines.add("Designation: " + defaultValue(payslip.getDesignationName(), "—")
                + "  Department: " + defaultValue(payslip.getDepartmentName(), "—"));
        lines.add("Pay period: " + payslip.getMonth());
        lines.add("");
        lines.add("EARNINGS");
        payslip.getEarnings().forEach(item -> lines.add(item.getName() + "    " + money(item.getAmount())));
        lines.add("Gross salary    " + money(payslip.getGrossSalary()));
        lines.add("");
        lines.add("DEDUCTIONS");
        payslip.getDeductions().forEach(item -> lines.add(item.getName() + "    " + money(item.getAmount())));
        lines.add("Total deductions    " + money(payslip.getTotalDeduction()));
        lines.add("");
        lines.add("NET SALARY    " + money(payslip.getNetSalary()));
        return createPdf(lines);
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
                totalDeductions = totalDeductions.add(amount);
                mergeComponentAmount(deductions, component, amount);
            }

            workingDays += monthlyAttendance.workingDays();
            paidDays = paidDays.add(monthlyAttendance.paidDays());
            unpaidDays = unpaidDays.add(BigDecimal.valueOf(monthlyAttendance.workingDays())
                    .subtract(monthlyAttendance.paidDays()));
            overtimeHours = overtimeHours.add(monthlyAttendance.overtimeHours());
        }

        item.setGrossSalary(round(gross));
        item.setTotalDeduction(round(totalDeductions));
        item.setNetSalary(round(gross.subtract(totalDeductions)));
        item.setWorkingDays(workingDays);
        item.setPaidDays(paidDays);
        item.setUnpaidDays(unpaidDays);
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
        for (Attendance record : monthAttendance) {
            if (record.getAttendanceDate() == null) continue;
            if ("ABSENT".equals(record.getStatus())) unpaid.put(record.getAttendanceDate(), BigDecimal.ONE);
            else if ("HALF_DAY".equals(record.getStatus())) unpaid.put(record.getAttendanceDate(), new BigDecimal("0.5"));
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
        return new MonthlyAttendance(workingDays, BigDecimal.valueOf(workingDays).subtract(unpaidDays), overtime);
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
        return mongo.findOne(Query.query(Criteria.where("month").is(month)), PayrollRun.class);
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

    private static byte[] createPdf(List<String> lines) {
        try {
            StringBuilder text = new StringBuilder("q 0.05 0.42 0.35 rg 52 790 28 28 re f Q\n")
                    .append("q 1 1 1 rg 62 794 8 20 re f 56 800 20 8 re f Q\n")
                    .append("BT /F1 15 Tf 88 803 Td (MEDCARE) Tj ET\n")
                    .append("BT /F1 9 Tf 88 790 Td (MONTHLY SALARY SLIP) Tj ET\n")
                    .append("BT /F1 11 Tf 52 766 Td 15 TL\n");
            for (String line : lines) {
                String safe = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
                text.append('(').append(safe).append(") Tj T*\n");
            }
            text.append("ET");
            byte[] stream = text.toString().getBytes(StandardCharsets.US_ASCII);
            List<byte[]> objects = List.of(
                    "<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII),
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(StandardCharsets.US_ASCII),
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>".getBytes(StandardCharsets.US_ASCII),
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".getBytes(StandardCharsets.US_ASCII),
                    ("<< /Length " + stream.length + " >>\nstream\n" + text + "\nendstream").getBytes(StandardCharsets.US_ASCII));
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
            output.write(("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n").getBytes(StandardCharsets.US_ASCII));
            for (int offset : offsets) output.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
            output.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                    + xref + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not generate payslip PDF", exception);
        }
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

    private record MonthlyAttendance(int workingDays, BigDecimal paidDays, BigDecimal overtimeHours) {}
}
