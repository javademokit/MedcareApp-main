package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.billing.AppointmentInvoice;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.services.AppointmentService;
import com.example.MedcareApp.services.UserService;
import com.example.MedcareApp.services.PatientIdentifierService;
import com.example.MedcareApp.services.PaymentGatewayService;
import com.example.MedcareApp.web.PaymentCheckoutRequest;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/patient-portal")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {
    private final PatientRepository patientRepository;
    private final UserService userService;
    private final AppointmentService appointmentService;
    private final PatientIdentifierService patientIdentifierService;
    private final PaymentGatewayService paymentGatewayService;

    public PatientPortalController(
            PatientRepository patientRepository,
            UserService userService,
            AppointmentService appointmentService,
            PatientIdentifierService patientIdentifierService,
            PaymentGatewayService paymentGatewayService) {
        this.patientRepository = patientRepository;
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.patientIdentifierService = patientIdentifierService;
        this.paymentGatewayService = paymentGatewayService;
    }

    @GetMapping("/profile")
    public Patient getProfile(Principal principal) {
        return getLinkedPatient(principal);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            Principal principal,
            @RequestBody PatientProfileUpdateRequest request) {
        if (request == null || request.patientAge() == null || request.patientAge().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Age is required");
        }
        int age;
        try {
            age = Integer.parseInt(request.patientAge().trim());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter age as a whole number");
        }
        if (age < 0 || age > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Age must be between 0 and 150");
        }
        if (request.dateOfBirth() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date of birth is required");
        }
        if (request.dateOfBirth().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date of birth cannot be in the future");
        }
        if (request.gender() == null || request.gender().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gender is required");
        }
        if (request.patientAddress() == null || request.patientAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address is required");
        }

        Patient patient = getLinkedPatient(principal);
        patient.setPatientAge(Integer.toString(age));
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender().trim());
        patient.setPatientAddress(request.patientAddress().trim());
        patientIdentifierService.updateIdentifiers(
                patient, request.patientPan(), request.patientAadhaar(), request.patientPostalCode());
        return ResponseEntity.ok(patientRepository.save(patient));
    }

    public record PatientProfileUpdateRequest(
            String patientAge, String gender, LocalDate dateOfBirth, String patientAddress,
            String patientPan, String patientAadhaar, String patientPostalCode) {}

    @GetMapping("/appointments")
    public List<Appointment> getAppointments(Principal principal) {
        Patient patient = getLinkedPatient(principal);
        return appointmentService.getAppointmentsForPatient(patient.getPatientId());
    }

    @GetMapping("/payments/gateways")
    public Map<String, Object> getPaymentGateways(Principal principal) {
        getLinkedPatient(principal);
        return paymentGatewayService.configuredGateways();
    }

    @PostMapping("/appointments/{appointmentId}/checkout")
    public Map<String, Object> createAppointmentCheckout(
            Principal principal,
            @PathVariable String appointmentId,
            @Valid @RequestBody PaymentCheckoutRequest request) {
        Appointment appointment = findOwnedAppointment(principal, appointmentId);
        if (appointment.getInvoiceId() == null || appointment.getInvoiceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No payable invoice was found for this appointment");
        }
        return paymentGatewayService.createCheckout(appointment.getInvoiceId(), request.getProvider());
    }

    @PostMapping("/appointments/{appointmentId}/verify")
    public AppointmentInvoice verifyAppointmentCheckout(
            Principal principal,
            @PathVariable String appointmentId,
            @Valid @RequestBody PaymentVerificationRequest request) {
        Appointment appointment = findOwnedAppointment(principal, appointmentId);
        if (appointment.getInvoiceId() == null || appointment.getInvoiceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No payable invoice was found for this appointment");
        }
        return paymentGatewayService.verifyCheckout(appointment.getInvoiceId(), request);
    }

    @GetMapping("/patients/search")
    public List<PatientLookupResult> searchPatients(
            Principal principal,
            @RequestParam String name,
            @RequestParam(required = false) String mobile) {
        getLinkedPatient(principal);
        if (name.trim().length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter at least 2 characters of the patient name");
        }
        String normalizedMobile = normalizeMobile(mobile);
        if (normalizedMobile.length() > 0 && normalizedMobile.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter the patient's full mobile number or search by name only");
        }
        String nameQuery = name.trim().toLowerCase(Locale.ROOT);
        return patientRepository.findAll().stream()
                .filter(patient -> patient.getPatientName() != null
                        && patient.getPatientName().toLowerCase(Locale.ROOT).contains(nameQuery))
                .filter(patient -> normalizedMobile.isEmpty()
                        || normalizeMobile(patient.getPatientmobileNo()).equals(normalizedMobile))
                .sorted(java.util.Comparator.comparing(
                        Patient::getPatientName, String.CASE_INSENSITIVE_ORDER))
                .limit(10)
                .map(patient -> new PatientLookupResult(
                        patient.getPatientId(),
                        patient.getPatientName(),
                        normalizeMobile(patient.getPatientmobileNo()).length() >= 4
                                ? "•••• " + normalizeMobile(patient.getPatientmobileNo())
                                        .substring(normalizeMobile(patient.getPatientmobileNo()).length() - 4)
                                : ""))
                .toList();
    }

    @GetMapping("/availability")
    public List<String> getAvailability(
            Principal principal,
            @RequestParam String doctorId,
            @RequestParam String date) {
        getLinkedPatient(principal);
        return appointmentService.getAvailableTimes(doctorId, date);
    }

    @PostMapping("/appointments")
    public ResponseEntity<?> bookAppointment(
            Principal principal,
            @RequestBody Appointment request) {
        try {
            Patient accountPatient = getLinkedPatient(principal);
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment details are required");
            }
            if (request.isRegisterNewPatient()) {
                request.setPatientId(null);
                request.setPatientEmailId(null);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(appointmentService.bookAppointment(request));
            }

            if (request.getPatientName() == null || request.getPatientName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient name is required");
            }
            String patientId = request.getPatientId() == null || request.getPatientId().isBlank()
                    ? accountPatient.getPatientId()
                    : request.getPatientId();
            Patient selectedPatient = findPatient(patientId);
            boolean bookingForAnotherPatient = !patientId.equals(accountPatient.getPatientId());
            if (bookingForAnotherPatient) {
                String submittedMobile = normalizeMobile(request.getMobileNo());
                if (submittedMobile.length() < 10) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Enter the selected patient's full mobile number to verify the booking");
                }
                boolean nameMatches = selectedPatient.getPatientName() != null
                        && request.getPatientName() != null
                        && selectedPatient.getPatientName().equalsIgnoreCase(request.getPatientName().trim());
                boolean mobileMatches = normalizeMobile(selectedPatient.getPatientmobileNo())
                        .equals(submittedMobile);
                if (!nameMatches || !mobileMatches) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No matching patient was found. Verify the name and mobile number.");
                }
            } else if (selectedPatient.getPatientName() == null || selectedPatient.getPatientName().isBlank()) {
                selectedPatient.setPatientName(request.getPatientName().trim());
                patientRepository.save(selectedPatient);
            } else if (!selectedPatient.getPatientName().equalsIgnoreCase(request.getPatientName().trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Patient name must match your profile. Update your profile before booking.");
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(appointmentService.bookForPatient(patientId, request));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(java.util.Map.of(
                    "message", exception.getReason() == null
                            ? "Appointment could not be booked"
                            : exception.getReason()));
        }
    }

    private Patient getLinkedPatient(Principal principal) {
        user account = userService.getUserByEmail(principal.getName());
        if (account == null || !account.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable");
        }
        List<Patient> patients = patientRepository.findAllByPatientEmailIdIgnoreCase(account.getEmailId());
        if (patients.isEmpty()) {
            return userService.ensurePatientProfileForUser(account);
        }
        if (patients.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Multiple patient profiles use this email. Contact reception to resolve the records.");
        }
        return patients.get(0);
    }

    private Patient findPatient(String patientId) {
        List<Patient> matches = patientRepository.findAllByPatientId(patientId);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient ID not found");
        }
        if (matches.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient ID is not unique");
        }
        return matches.get(0);
    }

    private Appointment findOwnedAppointment(Principal principal, String appointmentId) {
        Patient patient = getLinkedPatient(principal);
        return appointmentService.getAppointmentsForPatient(patient.getPatientId()).stream()
                .filter(appointment -> appointment.getId().equals(appointmentId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Appointment was not found for this patient"));
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null) return "";
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) return digits.substring(1);
        return digits;
    }

    public record PatientLookupResult(String patientId, String patientName, String maskedMobile) {}
}
