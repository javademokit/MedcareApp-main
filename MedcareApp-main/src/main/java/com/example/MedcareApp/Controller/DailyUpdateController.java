package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.DailyUpdate;
import com.example.MedcareApp.Interafce.DailyUpdateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/daily-updates")
public class DailyUpdateController {

    @Autowired
    private DailyUpdateRepository repository;

    @PostMapping
    public ResponseEntity<DailyUpdate> createUpdate(@RequestBody DailyUpdate update) {
        return ResponseEntity.ok(repository.save(update));
    }

    @GetMapping("/{patientId}")
    public List<DailyUpdate> getUpdatesByPatient(@PathVariable String patientId) {
        return repository.findByPatientId(patientId);
    }
}
