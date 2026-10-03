package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.web.PatientAdmissionRequest;
import com.example.MedcareApp.services.NursingService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientRepository patientRepository;
    private final NursingService nursingService;

    public PatientController(PatientRepository patientRepository, NursingService nursingService) {
        this.patientRepository = patientRepository;
        this.nursingService = nursingService;
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
            @Valid @RequestBody PatientAdmissionRequest request,
            Principal principal) {
        String wardId = request.getWardId();
        if (wardId == null || wardId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a configured ward");
        }
        if (request.getBedId() == null || request.getBedId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a vacant bed");
        }
        Patient patient = nursingService.admitPatient(
                patientId, wardId, request.getBedId(), principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(patient);
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
