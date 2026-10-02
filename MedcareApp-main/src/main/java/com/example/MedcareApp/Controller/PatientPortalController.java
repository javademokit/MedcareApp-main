package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.services.AppointmentService;
import com.example.MedcareApp.services.UserService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public PatientPortalController(
            PatientRepository patientRepository,
            UserService userService,
            AppointmentService appointmentService) {
        this.patientRepository = patientRepository;
        this.userService = userService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/profile")
    public Patient getProfile(Principal principal) {
        return getLinkedPatient(principal);
    }

    @GetMapping("/appointments")
    public List<Appointment> getAppointments(Principal principal) {
        Patient patient = getLinkedPatient(principal);
        return appointmentService.getAppointmentsForPatient(patient.getPatientId());
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
            Patient patient = getLinkedPatient(principal);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(appointmentService.bookForPatient(patient.getPatientId(), request));
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
}
