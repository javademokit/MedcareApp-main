package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.web.PatientAdmissionRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientRepository patientRepository;

    public PatientController(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @GetMapping
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<Patient> getPatient(@PathVariable String patientId) {
        return ResponseEntity.ok(findPatient(patientId));
    }

    @PostMapping("/{patientId}/admission")
    public ResponseEntity<Patient> admitPatient(
            @PathVariable String patientId,
            @Valid @RequestBody PatientAdmissionRequest request) {
        Patient patient = findPatient(patientId);
        if (patient.getPatientAdmitdate() != null && !patient.getPatientAdmitdate().isBlank()
                && (patient.getPatientDischargedate() == null || patient.getPatientDischargedate().isBlank())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient is already admitted");
        }
        patient.setPatientAdmitdate(LocalDate.now().toString());
        patient.setPatientDischargedate(null);
        patient.setPatientWardnum(request.getWardNumber().trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(patientRepository.save(patient));
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
}
