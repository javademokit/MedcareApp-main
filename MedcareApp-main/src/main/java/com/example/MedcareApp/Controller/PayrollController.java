package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.nursing.NurseProfile;
import com.example.MedcareApp.Entity.payroll.EmployeeSalary;
import com.example.MedcareApp.Entity.payroll.DoctorDispute;
import com.example.MedcareApp.Entity.payroll.DoctorEarning;
import com.example.MedcareApp.Entity.payroll.DoctorPayProfile;
import com.example.MedcareApp.Entity.payroll.DoctorRateCard;
import com.example.MedcareApp.Entity.payroll.NurseAttendanceSummary;
import com.example.MedcareApp.Entity.payroll.NurseWardAllowance;
import com.example.MedcareApp.Entity.payroll.PayrollAdjustment;
import com.example.MedcareApp.Entity.payroll.PayrollCycle;
import com.example.MedcareApp.Entity.payroll.PayrollLoan;
import com.example.MedcareApp.Entity.payroll.PayrollOvertime;
import com.example.MedcareApp.Entity.payroll.SalaryComponent;
import com.example.MedcareApp.Entity.payroll.SalaryStructure;
import com.example.MedcareApp.Entity.payroll.StatutoryConfig;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.nursing.NurseProfileRepository;
import com.example.MedcareApp.services.PayrollService;
import com.example.MedcareApp.services.StaffIdentifierGenerator;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {
    private final PayrollService payrollService;
    private final DoctorRepository doctorRepository;
    private final NurseProfileRepository nurseRepository;

    public PayrollController(
            PayrollService payrollService,
            DoctorRepository doctorRepository,
            NurseProfileRepository nurseRepository) {
        this.payrollService = payrollService;
        this.doctorRepository = doctorRepository;
        this.nurseRepository = nurseRepository;
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<Map<String, Object>> employees(@RequestParam String type, Principal principal) {
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("DOCTOR".equals(normalized)) {
            return doctorRepository.findAll().stream().map(doctor -> {
                if (doctor.getEmployeeId() == null || !doctor.getEmployeeId().startsWith("DT-")) {
                    doctor.setEmployeeId(StaffIdentifierGenerator.generate("DT"));
                    doctorRepository.save(doctor);
                }
                return Map.<String, Object>of(
                        "id", doctor.getId(), "employeeCode", nullSafe(doctor.getEmployeeId()),
                        "name", nullSafe(doctor.getDoctorName()), "type", "DOCTOR");
            }).toList();
        }
        if (!"NURSE".equals(normalized)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Employee type must be DOCTOR or NURSE");
        }
        return nurseRepository.findAll().stream().map(nurse -> {
            if (nurse.getEmployeeId() == null || !nurse.getEmployeeId().startsWith("NS-")) {
                nurse.setEmployeeId(StaffIdentifierGenerator.generate("NS"));
                nurseRepository.save(nurse);
            }
            return Map.<String, Object>of(
                    "id", nullSafe(nurse.getAccountId()), "employeeCode", nullSafe(nurse.getEmployeeId()),
                    "name", nullSafe(nurse.getName()), "type", "NURSE");
        }).toList();
    }

    @GetMapping("/components")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<SalaryComponent> components(Principal principal) {
        return payrollService.components(actor(principal));
    }

    @PostMapping("/components")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public SalaryComponent createComponent(@RequestBody SalaryComponent component, Principal principal) {
        return payrollService.saveComponent(component, actor(principal));
    }

    @PutMapping("/components/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public SalaryComponent updateComponent(
            @PathVariable String id, @RequestBody SalaryComponent component, Principal principal) {
        component.setId(id);
        return payrollService.saveComponent(component, actor(principal));
    }

    @GetMapping("/structures")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<SalaryStructure> structures(Principal principal) {
        return payrollService.structures(actor(principal));
    }

    @PostMapping("/structures")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public SalaryStructure createStructure(@RequestBody SalaryStructure structure, Principal principal) {
        return payrollService.createStructure(structure, actor(principal));
    }

    @PutMapping("/structures/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public SalaryStructure reviseStructure(
            @PathVariable String id, @RequestBody SalaryStructure structure, Principal principal) {
        return payrollService.reviseStructure(id, structure, actor(principal));
    }

    @GetMapping("/salaries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<EmployeeSalary> salaries(@RequestParam String type, Principal principal) {
        return payrollService.salaries(type, actor(principal));
    }

    @PostMapping("/employee-salary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public EmployeeSalary assignSalary(@RequestBody EmployeeSalary salary, Principal principal) {
        return payrollService.assignSalary(salary, actor(principal));
    }

    @GetMapping("/statutory-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<StatutoryConfig> statutoryConfigs(Principal principal) {
        return payrollService.statutoryConfigs(actor(principal));
    }

    @PostMapping("/statutory-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public StatutoryConfig saveStatutoryConfig(
            @RequestBody StatutoryConfig config, Principal principal) {
        return payrollService.saveStatutoryConfig(config, actor(principal));
    }

    @GetMapping("/doctor-pay-profiles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<DoctorPayProfile> doctorPayProfiles(Principal principal) {
        return payrollService.doctorPayProfiles(actor(principal));
    }

    @PostMapping("/doctors/{doctorId}/pay-profile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public DoctorPayProfile doctorPayProfile(
            @PathVariable String doctorId, @RequestBody DoctorPayProfile profile, Principal principal) {
        profile.setDoctorId(doctorId);
        return payrollService.saveDoctorPayProfile(profile, actor(principal));
    }

    @GetMapping("/doctor-rates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<DoctorRateCard> doctorRates(Principal principal) {
        return payrollService.doctorRateCards(actor(principal));
    }

    @PostMapping("/doctor-rates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public DoctorRateCard doctorRate(@RequestBody DoctorRateCard rate, Principal principal) {
        return payrollService.saveDoctorRateCard(rate, actor(principal));
    }

    @GetMapping("/doctors/{doctorId}/earnings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<DoctorEarning> doctorEarnings(
            @PathVariable String doctorId, Principal principal) {
        return payrollService.doctorEarnings(doctorId, actor(principal), false);
    }

    @GetMapping("/doctor-earnings/mine")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<DoctorEarning> myDoctorEarnings(Principal principal) {
        return payrollService.doctorEarnings(null, actor(principal), true);
    }

    @GetMapping("/doctor-earnings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<DoctorEarning> allDoctorEarnings(Principal principal) {
        return payrollService.allDoctorEarnings(actor(principal));
    }

    @PostMapping("/doctor-earnings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public DoctorEarning captureDoctorEarning(
            @RequestBody DoctorEarning earning, Principal principal) {
        return payrollService.captureDoctorEarning(earning, actor(principal));
    }

    @PutMapping("/doctor-earnings/{id}/reverse")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public DoctorEarning reverseDoctorEarning(
            @PathVariable String id, @RequestBody Map<String, String> request, Principal principal) {
        return payrollService.reverseDoctorEarning(id, request.get("reason"), actor(principal));
    }

    @PostMapping("/doctor-earnings/{id}/dispute")
    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorDispute disputeDoctorEarning(
            @PathVariable String id, @RequestBody Map<String, String> request, Principal principal) {
        return payrollService.disputeDoctorEarning(id, request.get("reason"), actor(principal));
    }

    @GetMapping("/doctor-disputes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<DoctorDispute> doctorDisputes(Principal principal) {
        return payrollService.doctorDisputes(actor(principal));
    }

    @PutMapping("/doctor-disputes/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public DoctorDispute resolveDoctorDispute(
            @PathVariable String id, @RequestBody Map<String, String> request, Principal principal) {
        return payrollService.resolveDoctorDispute(id, request.get("resolution"), actor(principal));
    }

    @GetMapping("/nurse-ward-allowances")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<NurseWardAllowance> nurseWardAllowances(Principal principal) {
        return payrollService.wardAllowances(actor(principal));
    }

    @PostMapping("/nurse-ward-allowances")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public NurseWardAllowance nurseWardAllowance(
            @RequestBody NurseWardAllowance allowance, Principal principal) {
        return payrollService.saveWardAllowance(allowance, actor(principal));
    }

    @GetMapping("/cycles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<PayrollCycle> cycles(Principal principal) {
        return payrollService.cycles(actor(principal));
    }

    @PostMapping("/cycles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollCycle createCycle(@RequestBody Map<String, Object> request, Principal principal) {
        return payrollService.createCycle(
                String.valueOf(request.get("type")),
                integer(request.get("month")), integer(request.get("year")), actor(principal));
    }

    @PostMapping("/cycles/{id}/calculate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollCycle calculate(@PathVariable String id, Principal principal) {
        return payrollService.calculateCycle(id, actor(principal));
    }

    @GetMapping("/cycles/{id}/entries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollCycle entries(@PathVariable String id, Principal principal) {
        return payrollService.cycle(id, actor(principal));
    }

    @PutMapping("/cycles/{id}/{action:submit-review|approve|lock|mark-paid}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollCycle transition(
            @PathVariable String id, @PathVariable String action, Principal principal) {
        return payrollService.transition(id, action, actor(principal));
    }

    @PutMapping("/cycles/{id}/review-exceptions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollCycle reviewExceptions(
            @PathVariable String id, @RequestBody Map<String, String> request, Principal principal) {
        return payrollService.reviewExceptions(id, request.get("note"), actor(principal));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollAdjustment adjustment(@RequestBody PayrollAdjustment adjustment, Principal principal) {
        return payrollService.createAdjustment(adjustment, actor(principal));
    }

    @PutMapping("/adjustments/{id}/{decision:approve|reject}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE')")
    public PayrollAdjustment decideAdjustment(
            @PathVariable String id, @PathVariable String decision, Principal principal) {
        return payrollService.decideAdjustment(id, "approve".equals(decision), actor(principal));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<PayrollAdjustment> adjustments(Principal principal) {
        return payrollService.adjustments(actor(principal));
    }

    @PostMapping("/loans")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollLoan loan(@RequestBody PayrollLoan loan, Principal principal) {
        return payrollService.createLoan(loan, actor(principal));
    }

    @GetMapping("/loans")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public List<PayrollLoan> loans(Principal principal) {
        return payrollService.loans(actor(principal));
    }

    @GetMapping("/nurse-overtime")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR','HEAD_NURSE')")
    public List<PayrollOvertime> overtime(Principal principal) {
        return payrollService.overtimeList(actor(principal));
    }

    @PostMapping("/nurse-overtime")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','HEAD_NURSE')")
    public PayrollOvertime overtime(@RequestBody PayrollOvertime request, Principal principal) {
        return payrollService.submitOvertime(request, actor(principal));
    }

    @PutMapping("/nurse-overtime/{id}/{decision:approve|reject}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public PayrollOvertime decideOvertime(
            @PathVariable String id, @PathVariable String decision,
            @RequestBody(required = false) Map<String, String> request, Principal principal) {
        return payrollService.decideOvertime(id, "approve".equals(decision),
                request == null ? null : request.get("note"), actor(principal));
    }

    @GetMapping("/nurse-attendance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR','HEAD_NURSE')")
    public List<NurseAttendanceSummary> attendance(Principal principal) {
        return payrollService.attendance(actor(principal));
    }

    @GetMapping("/nurse-attendance/mine")
    @PreAuthorize("hasAnyRole('NURSE','HEAD_NURSE')")
    public List<NurseAttendanceSummary> myAttendance(Principal principal) {
        return payrollService.myAttendance(actor(principal));
    }

    @PostMapping("/nurse-attendance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR','HEAD_NURSE')")
    public NurseAttendanceSummary attendance(
            @RequestBody NurseAttendanceSummary summary, Principal principal) {
        return payrollService.saveAttendance(summary, actor(principal));
    }

    @GetMapping("/reports/{reportName}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public ResponseEntity<String> report(@PathVariable String reportName, Principal principal) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payroll-report.csv\"")
                .body(payrollService.reportCsv(reportName, actor(principal)));
    }

    @GetMapping("/me/payslips")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','HEAD_NURSE')")
    public List<PayrollCycle> myPayslips(Principal principal) {
        return payrollService.myPayslips(actor(principal));
    }

    @GetMapping(value = "/entries/{id}/payslip.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','FINANCE','HR')")
    public ResponseEntity<byte[]> payslip(@PathVariable String id, Principal principal) {
        return pdfResponse(payrollService.payslipPdf(id, actor(principal), false));
    }

    @GetMapping(value = "/me/payslips/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','HEAD_NURSE')")
    public ResponseEntity<byte[]> myPayslip(@PathVariable String id, Principal principal) {
        return pdfResponse(payrollService.payslipPdf(id, actor(principal), true));
    }

    private String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handlePayrollRequestError(ResponseStatusException exception) {
        String message = exception.getReason() == null
                ? exception.getStatusCode().toString() : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Month and year must be whole numbers");
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payslip.pdf\"")
                .body(bytes);
    }
}
