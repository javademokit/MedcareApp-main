package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.hrpayroll.Attendance;
import com.example.MedcareApp.Entity.hrpayroll.Department;
import com.example.MedcareApp.Entity.hrpayroll.Designation;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.hrpayroll.EmployeeType;
import com.example.MedcareApp.Entity.hrpayroll.LeaveRequest;
import com.example.MedcareApp.Entity.hrpayroll.PayrollRun;
import com.example.MedcareApp.Entity.hrpayroll.Payslip;
import com.example.MedcareApp.Entity.hrpayroll.SalaryComponent;
import com.example.MedcareApp.Entity.hrpayroll.SalaryStructure;
import com.example.MedcareApp.Entity.hrpayroll.Shift;
import com.example.MedcareApp.services.HrPayrollService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HrPayrollController {
    private static final String HR_ROLES = "hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','HR')";
    private static final String PAYROLL_ROLES = "hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','HR','FINANCE')";
    private static final String STAFF_ROLES = "hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','HR','FINANCE',"
            + "'DOCTOR','NURSE','HEAD_NURSE','RECEPTIONIST','CRM_EXECUTIVE','BILLING_EXECUTIVE','PHARMACIST','LAB_TECHNICIAN')";

    private final HrPayrollService service;

    public HrPayrollController(HrPayrollService service) {
        this.service = service;
    }

    @GetMapping("/hr/dashboard")
    @PreAuthorize(PAYROLL_ROLES)
    public Map<String, Object> dashboard(@RequestParam(required = false) String month) {
        return service.dashboard(month);
    }

    @GetMapping("/employee-types")
    @PreAuthorize(HR_ROLES)
    public List<EmployeeType> employeeTypes() {
        return service.employeeTypes();
    }

    @PostMapping("/employee-types")
    @PreAuthorize(HR_ROLES)
    public EmployeeType createEmployeeType(@RequestBody EmployeeType type) {
        return service.saveEmployeeType(type);
    }

    @PutMapping("/employee-types/{id}")
    @PreAuthorize(HR_ROLES)
    public EmployeeType updateEmployeeType(@PathVariable String id, @RequestBody EmployeeType type) {
        type.setId(id);
        return service.saveEmployeeType(type);
    }

    @DeleteMapping("/employee-types/{id}")
    @PreAuthorize(HR_ROLES)
    public ResponseEntity<Void> deleteEmployeeType(@PathVariable String id) {
        service.deactivateEmployeeType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employees")
    @PreAuthorize(HR_ROLES)
    public List<Map<String, Object>> employees() {
        return service.employees();
    }

    @PostMapping("/employees")
    @PreAuthorize(HR_ROLES)
    public Employee createEmployee(@RequestBody Employee employee) {
        return service.saveEmployee(employee);
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize(HR_ROLES)
    public Employee employee(@PathVariable String id) {
        return service.employee(id);
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize(HR_ROLES)
    public Employee updateEmployee(@PathVariable String id, @RequestBody Employee employee) {
        return service.updateEmployee(id, employee);
    }

    @DeleteMapping("/employees/{id}")
    @PreAuthorize(HR_ROLES)
    public ResponseEntity<Void> deleteEmployee(@PathVariable String id) {
        service.deactivateEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/departments")
    @PreAuthorize(HR_ROLES)
    public List<Department> departments() {
        return service.departments();
    }

    @PostMapping("/departments")
    @PreAuthorize(HR_ROLES)
    public Department createDepartment(@RequestBody Department department) {
        return service.saveDepartment(department);
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize(HR_ROLES)
    public Department updateDepartment(@PathVariable String id, @RequestBody Department department) {
        department.setId(id);
        return service.saveDepartment(department);
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize(HR_ROLES)
    public ResponseEntity<Void> deleteDepartment(@PathVariable String id) {
        service.deactivateDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/designations")
    @PreAuthorize(HR_ROLES)
    public List<Map<String, Object>> designations() {
        return service.designations();
    }

    @PostMapping("/designations")
    @PreAuthorize(HR_ROLES)
    public Designation createDesignation(@RequestBody Designation designation) {
        return service.saveDesignation(designation);
    }

    @PutMapping("/designations/{id}")
    @PreAuthorize(HR_ROLES)
    public Designation updateDesignation(@PathVariable String id, @RequestBody Designation designation) {
        designation.setId(id);
        return service.saveDesignation(designation);
    }

    @DeleteMapping("/designations/{id}")
    @PreAuthorize(HR_ROLES)
    public ResponseEntity<Void> deleteDesignation(@PathVariable String id) {
        service.deactivateDesignation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shifts")
    @PreAuthorize(HR_ROLES)
    public List<Shift> shifts() {
        return service.shifts();
    }

    @PostMapping("/shifts")
    @PreAuthorize(HR_ROLES)
    public Shift createShift(@RequestBody Shift shift) {
        return service.saveShift(shift);
    }

    @PutMapping("/shifts/{id}")
    @PreAuthorize(HR_ROLES)
    public Shift updateShift(@PathVariable String id, @RequestBody Shift shift) {
        shift.setId(id);
        return service.saveShift(shift);
    }

    @DeleteMapping("/shifts/{id}")
    @PreAuthorize(HR_ROLES)
    public ResponseEntity<Void> deleteShift(@PathVariable String id) {
        service.deactivateShift(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attendance")
    @PreAuthorize(HR_ROLES)
    public List<Map<String, Object>> attendance(@RequestParam(required = false) String month) {
        return service.attendance(month);
    }

    @GetMapping("/attendance/employee/{employeeId}")
    @PreAuthorize(HR_ROLES)
    public List<Map<String, Object>> attendanceForEmployee(@PathVariable String employeeId) {
        return service.attendanceForEmployee(employeeId);
    }

    @PostMapping("/attendance")
    @PreAuthorize(HR_ROLES)
    public Attendance createAttendance(@RequestBody Attendance attendance) {
        return service.saveAttendance(attendance);
    }

    @GetMapping("/leaves")
    @PreAuthorize(HR_ROLES)
    public List<Map<String, Object>> leaves(@RequestParam(required = false) String month) {
        return service.leaves(month);
    }

    @GetMapping("/leaves/mine")
    @PreAuthorize(STAFF_ROLES)
    public List<Map<String, Object>> myLeaves(Principal principal) {
        return service.myLeaves(principal.getName());
    }

    @PostMapping("/leaves")
    @PreAuthorize(STAFF_ROLES)
    public LeaveRequest createLeave(@RequestBody LeaveRequest leave, Authentication authentication) {
        return service.createLeave(leave, authentication.getName(), isHr(authentication));
    }

    @PutMapping("/leaves/{id}/approve")
    @PreAuthorize(HR_ROLES)
    public LeaveRequest approveLeave(@PathVariable String id, Principal principal) {
        return service.decideLeave(id, "APPROVE", principal.getName());
    }

    @PutMapping("/leaves/{id}/reject")
    @PreAuthorize(HR_ROLES)
    public LeaveRequest rejectLeave(@PathVariable String id, Principal principal) {
        return service.decideLeave(id, "REJECT", principal.getName());
    }

    @GetMapping("/salary/components")
    @PreAuthorize(PAYROLL_ROLES)
    public List<SalaryComponent> salaryComponents() {
        return service.salaryComponents();
    }

    @PostMapping("/salary/components")
    @PreAuthorize(PAYROLL_ROLES)
    public SalaryComponent createSalaryComponent(@RequestBody SalaryComponent component) {
        return service.saveSalaryComponent(component);
    }

    @PutMapping("/salary/components/{id}")
    @PreAuthorize(PAYROLL_ROLES)
    public SalaryComponent updateSalaryComponent(@PathVariable String id, @RequestBody SalaryComponent component) {
        component.setId(id);
        return service.saveSalaryComponent(component);
    }

    @GetMapping("/salary/structures")
    @PreAuthorize(PAYROLL_ROLES)
    public List<Map<String, Object>> salaryStructures(@RequestParam(required = false) String employeeId) {
        return service.salaryStructures(employeeId);
    }

    @GetMapping("/salary/structures/{employeeId}")
    @PreAuthorize(PAYROLL_ROLES)
    public List<Map<String, Object>> salaryStructuresForEmployee(@PathVariable String employeeId) {
        return service.salaryStructures(employeeId);
    }

    @PostMapping("/salary/structures")
    @PreAuthorize(PAYROLL_ROLES)
    public SalaryStructure createSalaryStructure(@RequestBody SalaryStructure structure) {
        return service.saveSalaryStructure(structure);
    }

    @GetMapping("/payroll")
    @PreAuthorize(PAYROLL_ROLES)
    public List<PayrollRun> payrollRuns(@RequestParam(required = false) String month) {
        return service.payrollRuns(month);
    }

    @PostMapping("/payroll/run")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun createPayrollRun(@RequestBody Map<String, String> request, Principal principal) {
        return service.createPayrollRun(request.get("month"), principal.getName());
    }

    @GetMapping("/payroll/{id}")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun payrollRun(@PathVariable String id) {
        return service.payrollRun(id);
    }

    @PostMapping("/payroll/{id}/calculate")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun calculatePayroll(@PathVariable String id) {
        return service.calculatePayroll(id);
    }

    @PostMapping("/payroll/{id}/approve")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun approvePayroll(@PathVariable String id, Principal principal) {
        return service.approvePayroll(id, principal.getName());
    }

    @PostMapping("/payroll/{id}/reject")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun rejectPayroll(@PathVariable String id, @RequestBody(required = false) Map<String, String> request) {
        return service.rejectPayroll(id, request == null ? null : request.get("reason"));
    }

    @PostMapping("/payroll/{id}/reset")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun resetRejectedPayroll(@PathVariable String id) {
        return service.resetRejectedPayroll(id);
    }

    @PostMapping("/payroll/{id}/process")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun processPayroll(@PathVariable String id) {
        return service.processPayroll(id);
    }

    @PostMapping("/payroll/{id}/paid")
    @PreAuthorize(PAYROLL_ROLES)
    public PayrollRun markPayrollPaid(@PathVariable String id) {
        return service.markPayrollPaid(id);
    }

    @GetMapping("/payroll/me/payslips")
    @PreAuthorize(STAFF_ROLES)
    public List<Payslip> myPayslips(Principal principal) {
        return service.myPayslips(principal.getName());
    }

    @GetMapping("/payslips")
    @PreAuthorize(PAYROLL_ROLES)
    public List<Payslip> payslips(@RequestParam(required = false) String month) {
        return service.payslips(month);
    }

    @GetMapping("/payslips/{employeeId}/{month}")
    @PreAuthorize(STAFF_ROLES)
    public List<Payslip> employeePayslips(
            @PathVariable String employeeId, @PathVariable String month, Authentication authentication) {
        if (!isPayrollManager(authentication) && !service.ownsEmployee(employeeId, authentication.getName())) {
            throw new AccessDeniedException("Employees may only view their own payslips");
        }
        return service.payslipsForEmployeeMonth(employeeId, month);
    }

    @GetMapping("/payslips/{id}/download")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable String id, Authentication authentication) {
        Payslip payslip = service.payslip(id);
        if (!isPayrollManager(authentication) && !service.ownsPayslip(payslip, authentication.getName())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + payslip.getMonth() + "-" + payslip.getEmployeeCode() + ".pdf\"")
                .body(service.payslipPdf(payslip));
    }

    @GetMapping("/reports/hr")
    @PreAuthorize(PAYROLL_ROLES)
    public ResponseEntity<?> reports(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hr-report.csv\"")
                    .body(service.reportCsv(month));
        }
        return ResponseEntity.ok(service.reports(month));
    }

    private boolean isHr(Authentication authentication) {
        return hasAnyRole(authentication, "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR");
    }

    private boolean isPayrollManager(Authentication authentication) {
        return hasAnyRole(authentication, "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE");
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> List.of(roles).contains(authority.getAuthority().replaceFirst("^ROLE_", "")));
    }
}
