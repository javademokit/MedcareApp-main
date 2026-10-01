package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    @Autowired
    private PatientRepository patientRepository;

    @GetMapping
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @PostMapping
    public Patient admitPatient(@RequestBody Patient patient) {
        return patientRepository.save(patient);
    }
}

