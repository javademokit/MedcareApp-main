package com.example.payroll.service;

import com.example.payroll.dto.Dtos.EmployeeDto;
import com.example.payroll.model.Employee;
import com.example.payroll.model.Structure;
import com.example.payroll.repo.EmployeeRepository;
import com.example.payroll.repo.StructureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private static final List<String> EMPLOYEE_ROLES = List.of(
            "HR Manager", "HR Executive", "Doctor", "MD / Consultant", "Nurse",
            "Ambulance Driver", "Medical Technician", "Lab Engineer", "Lab Technician",
            "Radiology Technician", "IT Engineer", "IT Support", "Pharmacist",
            "Physiotherapist", "Receptionist", "Billing / Finance", "Hospital Administrator",
            "Housekeeping", "HKP", "OPD", "OTT", "Security", "Other");

    private final EmployeeRepository repo;
    private final StructureRepository structures;
    private static final List<String> EMPLOYMENT_TYPES = List.of(
            "Full-time", "Part-time", "Contract", "Locum", "Trainee", "Temporary");

    public List<String> roles() {
        return EMPLOYEE_ROLES;
    }

    public List<EmployeeDto> list() {
        return repo.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    public boolean exists(Long id) {
        return repo.existsById(id);
    }

    public EmployeeDto save(Long id, EmployeeDto d) {
        validatePayrollDetails(d);
        Employee e = employeeFor(id, d);
        applyPayrollDetails(e, d);
        return toDto(repo.save(e));
    }

    public EmployeeDto onboard(Long id, EmployeeDto d) {
        validatePayrollDetails(d);
        if (d.joiningDate() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Joining date is required");
        if (d.employmentType() == null || !EMPLOYMENT_TYPES.contains(d.employmentType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid employment type");
        if (d.workEmail() == null || !d.workEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid work email is required");
        validatePhone(d.phone(), "Phone number");
        if ((d.emergencyContactName() == null || d.emergencyContactName().isBlank())
                != (d.emergencyContactPhone() == null || d.emergencyContactPhone().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter both emergency contact name and phone");
        if (d.emergencyContactPhone() != null && !d.emergencyContactPhone().isBlank())
            validatePhone(d.emergencyContactPhone(), "Emergency contact phone");
        if (requiresProfessionalRegistration(d.role())
                && (d.registrationCouncil() == null || d.registrationCouncil().isBlank()
                || d.professionalRegistrationNo() == null || d.professionalRegistrationNo().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Professional registration council and number are required for this clinical role");

        Employee e = employeeFor(id, d);
        applyPayrollDetails(e, d);
        e.setJoiningDate(d.joiningDate());
        e.setEmploymentType(d.employmentType());
        e.setWorkEmail(d.workEmail().trim());
        e.setPhone(d.phone().trim());
        e.setEmergencyContactName(trimToNull(d.emergencyContactName()));
        e.setEmergencyContactPhone(trimToNull(d.emergencyContactPhone()));
        e.setRegistrationCouncil(trimToNull(d.registrationCouncil()));
        e.setProfessionalRegistrationNo(trimToNull(d.professionalRegistrationNo()));
        e.setRegistrationExpiryDate(d.registrationExpiryDate());
        e.setIdentityVerified(Boolean.TRUE.equals(d.identityVerified()));
        e.setProfessionalCredentialVerified(Boolean.TRUE.equals(d.professionalCredentialVerified()));
        e.setContractSigned(Boolean.TRUE.equals(d.contractSigned()));
        e.setOrientationComplete(Boolean.TRUE.equals(d.orientationComplete()));
        e.setSystemAccessProvisioned(Boolean.TRUE.equals(d.systemAccessProvisioned()));
        return toDto(repo.save(e));
    }

    private void validatePayrollDetails(EmployeeDto d) {
        if (d.name() == null || d.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        if (d.department() == null || d.department().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is required");
        if (d.role() == null || d.role().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        if (d.monthlyGross() == null || d.monthlyGross().signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monthly gross must be zero or more");
        if (d.structureId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salary structure is required");
        validateIfscCode(d.ifscCode());
    }

    private Employee employeeFor(Long id, EmployeeDto d) {
        Employee e;
        if (id == null) {
            e = new Employee();
            e.setCode(nextEmployeeCode(d.role()));
        } else {
            e = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
            String requestedCode = d.code() == null ? "" : d.code().trim();
            if (!requestedCode.isEmpty() && !requestedCode.equals(e.getCode())) {
                if (!employeeCodeMatchesRole(requestedCode, d.role())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Employee ID must match the role-specific six-digit format");
                }
                boolean duplicate = repo.findAllByOrderByIdAsc().stream()
                        .anyMatch(existing -> !Objects.equals(existing.getId(), id)
                                && requestedCode.equals(existing.getCode()));
                if (duplicate) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee ID is already assigned");
                }
                e.setCode(requestedCode);
            }
        }
        Structure s = structures.findById(d.structureId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown salary structure"));
        e.setStructure(s);
        return e;
    }

    private String nextEmployeeCode(String role) {
        String prefix = employeeCodePrefix(role);
        long sequence = repo.findAllByOrderByIdAsc().stream()
                .map(Employee::getCode)
                .filter(code -> code != null && code.matches(prefix + "-[0-9]{6}"))
                .mapToLong(code -> Long.parseLong(code.substring(prefix.length() + 1)))
                .max()
                .orElse(0) + 1;
        return employeeCodeForRole(role, sequence);
    }

    static String employeeCodeForRole(String role, long sequence) {
        if (sequence < 1 || sequence > 999_999) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee ID sequence is exhausted");
        }
        return employeeCodePrefix(role) + "-" + String.format(Locale.ROOT, "%06d", sequence);
    }

    static boolean employeeCodeMatchesRole(String code, String role) {
        return code != null && code.matches(employeeCodePrefix(role) + "-[0-9]{6}");
    }

    private static String employeeCodePrefix(String role) {
        String normalizedRole = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        return normalizedRole.contains("doctor") || normalizedRole.contains("consultant") ? "DT"
                : normalizedRole.contains("nurse") ? "NR" : "EMP";
    }

    private void applyPayrollDetails(Employee e, EmployeeDto d) {
        e.setName(d.name().trim());
        e.setDepartment(d.department().trim());
        e.setRole(d.role().trim());
        e.setMonthlyGross(d.monthlyGross());
        e.setBank(d.bank());
        if (d.bankAccountNumber() != null) {
            e.setBankAccountNumber(trimToNull(d.bankAccountNumber()));
        }
        if (d.ifscCode() != null) {
            e.setIfscCode(normalizeIfscCode(d.ifscCode()));
        }
        if (d.bankBranchName() != null) {
            e.setBankBranchName(trimToNull(d.bankBranchName()));
        }
        if (d.bankCity() != null) {
            e.setBankCity(trimToNull(d.bankCity()));
        }
    }

    private void validateIfscCode(String ifscCode) {
        String normalized = trimToNull(ifscCode);
        if (normalized != null && !normalized.toUpperCase(Locale.ROOT).matches("[A-Z]{4}0[A-Z0-9]{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 11-character IFSC code");
        }
    }

    private String normalizeIfscCode(String ifscCode) {
        String normalized = trimToNull(ifscCode);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private void validatePhone(String phone, String label) {
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        int digits = phone.replaceAll("\\D", "").length();
        if (digits < 7 || digits > 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must contain 7 to 15 digits");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean requiresProfessionalRegistration(String role) {
        String normalized = role.toLowerCase(Locale.ROOT);
        return List.of("doctor", "consultant", "nurse", "medical technician", "lab ",
                        "radiology", "pharmacist", "physiotherapist")
                .stream().anyMatch(normalized::contains);
    }

    private EmployeeDto toDto(Employee e) {
        return new EmployeeDto(e.getId(), e.getCode(), e.getName(), e.getDepartment(), e.getRole(),
                e.getMonthlyGross(), e.getBank(), e.getBankAccountNumber(), e.getIfscCode(),
                e.getBankBranchName(), e.getBankCity(), e.getStructure().getId(), e.getStructure().getName(),
                e.getJoiningDate(), e.getEmploymentType(), e.getWorkEmail(), e.getPhone(),
                e.getEmergencyContactName(), e.getEmergencyContactPhone(), e.getRegistrationCouncil(),
                e.getProfessionalRegistrationNo(), e.getRegistrationExpiryDate(), e.isIdentityVerified(),
                e.isProfessionalCredentialVerified(), e.isContractSigned(), e.isOrientationComplete(),
                e.isSystemAccessProvisioned());
    }
}
