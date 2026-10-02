package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.DailyUpdate;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.DailyUpdateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/daily-updates")
public class DailyUpdateController {

    private final DailyUpdateRepository repository;
    private final PatientRepository patientRepository;

    @Autowired
    public DailyUpdateController(DailyUpdateRepository repository, PatientRepository patientRepository) {
        this.repository = repository;
        this.patientRepository = patientRepository;
    }

    @PostMapping
    public ResponseEntity<DailyUpdate> createUpdate(@RequestBody DailyUpdate update) {
        if (update.getPatientId() == null || update.getPatientId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient ID is required");
        }
        if (patientRepository.findAllByPatientId(update.getPatientId()).size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient ID not found or is not unique");
        }
        return ResponseEntity.ok(repository.save(update));
    }

    @GetMapping("/{patientId}")
    public List<DailyUpdate> getUpdatesByPatient(@PathVariable String patientId) {
        return repository.findByPatientId(patientId);
    }
}
