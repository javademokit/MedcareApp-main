package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.PatientRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wards")
public class WardController {
    private final PatientRepository patientRepository;

    public WardController(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @GetMapping
    public List<WardOccupancy> getWardOccupancy() {
        return patientRepository.findAll().stream()
                .filter(patient -> patient.getPatientAdmitdate() != null
                        && !patient.getPatientAdmitdate().isBlank()
                        && (patient.getPatientDischargedate() == null
                        || patient.getPatientDischargedate().isBlank()))
                .map(patient -> new WardOccupancy(
                        patient.getPatientWardnum(),
                        "Not recorded",
                        patient.getPatientName(),
                        patient.getPatientId(),
                        patient.getPatientNurseassign()))
                .toList();
    }

    public record WardOccupancy(
            String name,
            String roomNumber,
            String patientName,
            String patientId,
            String assignedNurse) {}
}
