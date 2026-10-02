package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.Consultation;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.AppointmentRepository;
import com.example.MedcareApp.Interafce.ConsultationRepository;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.MedicalTestRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.services.UserService;
import com.example.MedcareApp.web.DoctorConsultationRequest;
import com.example.MedcareApp.testModel.MedicalTest;
import com.example.MedcareApp.testModel.MedicalTestType;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/doctor-portal")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorPortalController {
    private final UserService userService;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final ConsultationRepository consultationRepository;
    private final MedicalTestRepository medicalTestRepository;

    public DoctorPortalController(
            UserService userService,
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            ConsultationRepository consultationRepository,
            MedicalTestRepository medicalTestRepository) {
        this.userService = userService;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.consultationRepository = consultationRepository;
        this.medicalTestRepository = medicalTestRepository;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(Principal principal) {
        Doctor doctor = getLinkedDoctor(principal);
        String today = LocalDate.now().toString();
        List<Appointment> appointments = new java.util.ArrayList<>(
                appointmentRepository.findAllByDoctorIdAndDateGreaterThanEqualOrderByDateAscTimeAsc(
                        doctor.getId(), today));
        appointmentRepository.findAllByDoctorAndDateGreaterThanEqualOrderByDateAscTimeAsc(
                        doctor.getDoctorName(), today).stream()
                .filter(appointment -> !StringUtils.hasText(appointment.getDoctorId()))
                .filter(appointment -> isAssignedToDoctor(appointment, doctor))
                .forEach(appointments::add);
        appointments.sort(java.util.Comparator
                .comparing(Appointment::getDate, java.util.Comparator.nullsLast(String::compareTo))
                .thenComparing(Appointment::getTime, java.util.Comparator.nullsLast(String::compareTo)));
        long waiting = appointments.stream()
                .filter(appointment -> "pending".equalsIgnoreCase(appointment.getAppointmentStatus())
                        || "confirmed".equalsIgnoreCase(appointment.getAppointmentStatus()))
                .count();
        long followUps = appointments.stream()
                .filter(appointment -> appointment.getReason() != null
                        && appointment.getReason().toLowerCase().contains("follow"))
                .count();
        return Map.of(
                "doctor", doctor,
                "appointments", appointments,
                "waitingCount", waiting,
                "followUpCount", followUps,
                "fromDate", today);
    }

    @GetMapping("/patients/{patientId}")
    public Map<String, Object> getPatient360(Principal principal, @PathVariable String patientId) {
        Doctor doctor = getLinkedDoctor(principal);
        Patient patient = getUniquePatient(patientId);
        boolean isAssignedPatient = appointmentRepository
                .findAllByPatientIdOrderByDateDescTimeDesc(patientId).stream()
                .anyMatch(appointment -> isAssignedToDoctor(appointment, doctor));
        if (!isAssignedPatient) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient has no appointment with this doctor");
        }
        return Map.of(
                "patient", patient,
                "consultations", consultationRepository.findAllByPatientIdOrderByCreatedAtDesc(patientId));
    }

    @PostMapping("/consultations")
    public ResponseEntity<Consultation> completeConsultation(
            Principal principal,
            @Valid @RequestBody DoctorConsultationRequest request) {
        Doctor doctor = getLinkedDoctor(principal);
        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!isAssignedToDoctor(appointment, doctor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment is not assigned to this doctor");
        }
        if ("cancelled".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled appointments cannot be completed");
        }
        if (consultationRepository.existsByAppointmentId(appointment.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A consultation is already recorded for this appointment");
        }
        if (appointment.getPatientId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment has no linked Patient ID");
        }
        Patient patient = getUniquePatient(appointment.getPatientId());
        int patientAge = parseAge(patient.getPatientAge());
        List<MedicalTestType> labOrderTypes = request.getLabOrders() == null ? List.of()
                : request.getLabOrders().stream()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(this::parseTestType)
                        .toList();

        Consultation consultation = new Consultation();
        consultation.setAppointmentId(appointment.getId());
        consultation.setPatientId(appointment.getPatientId());
        consultation.setDoctorId(doctor.getId());
        consultation.setDoctorName(doctor.getDoctorName());
        consultation.setSymptoms(trimToNull(request.getSymptoms()));
        consultation.setBloodPressure(trimToNull(request.getBloodPressure()));
        consultation.setPulse(trimToNull(request.getPulse()));
        consultation.setTemperature(trimToNull(request.getTemperature()));
        consultation.setOxygenSaturation(trimToNull(request.getOxygenSaturation()));
        consultation.setWeight(trimToNull(request.getWeight()));
        consultation.setDiagnosis(request.getDiagnosis().trim());
        consultation.setPrescription(trimToNull(request.getPrescription()));
        consultation.setLabOrders(labOrderTypes.stream()
                .map(MedicalTestType::getDisplayName).toList());
        consultation.setDoctorNotes(trimToNull(request.getDoctorNotes()));
        consultation.setFollowUpDate(trimToNull(request.getFollowUpDate()));
        Consultation saved = consultationRepository.save(consultation);
        for (MedicalTestType testType : labOrderTypes) {
            MedicalTest order = new MedicalTest();
            order.setPatientId(appointment.getPatientId());
            order.setPatientName(patient.getPatientName());
            order.setAge(patientAge);
            order.setGender(patient.getGender());
            order.setReferredBy(doctor.getDoctorName());
            order.setTestType(testType);
            order.setRemarks("Ordered from consultation " + saved.getId());
            medicalTestRepository.save(order);
        }
        appointment.setAppointmentStatus("completed");
        appointmentRepository.save(appointment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private boolean isAssignedToDoctor(Appointment appointment, Doctor doctor) {
        if (StringUtils.hasText(appointment.getDoctorId())) {
            return doctor.getId().equals(appointment.getDoctorId());
        }
        List<Doctor> sameNameDoctors = doctorRepository.findAllByDoctorName(doctor.getDoctorName());
        return sameNameDoctors.size() == 1 && doctor.getId().equals(sameNameDoctors.get(0).getId());
    }

    private Doctor getLinkedDoctor(Principal principal) {
        user account = userService.getUserByEmail(principal.getName());
        if (account == null || !account.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable");
        }
        if (account.getDoctorId() == null || account.getDoctorId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No doctor profile is linked to this account. Contact your hospital administrator.");
        }
        return doctorRepository.findById(account.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The linked doctor profile no longer exists. Contact your hospital administrator."));
    }

    private Patient getUniquePatient(String patientId) {
        List<Patient> patients = patientRepository.findAllByPatientId(patientId);
        if (patients.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient ID not found");
        if (patients.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient ID is not unique");
        return patients.get(0);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MedicalTestType parseTestType(String value) {
        return java.util.Arrays.stream(MedicalTestType.values())
                .filter(type -> type.name().equalsIgnoreCase(value)
                        || type.getDisplayName().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown lab or diagnostic order: " + value));
    }

    private int parseAge(String age) {
        if (age == null || age.isBlank()) return 0;
        try {
            return Integer.parseInt(age);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient age is not a valid whole number");
        }
    }
}
