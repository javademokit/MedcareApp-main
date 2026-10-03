package com.example.MedcareApp.Controller;


import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Interafce.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.example.MedcareApp.services.StaffIdentifierGenerator;



import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    @Autowired
    private DoctorRepository doctorRepository;

    @GetMapping
    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll().stream().map(this::ensureEmployeeId).toList();
    }

    @PostMapping
    public Doctor createDoctor(@RequestBody Doctor doctor) {
        if (doctor.getEmployeeId() == null || !doctor.getEmployeeId().startsWith("DT-")) {
            doctor.setEmployeeId(StaffIdentifierGenerator.generate("DT"));
        }
        return doctorRepository.save(doctor);
    }

    private Doctor ensureEmployeeId(Doctor doctor) {
        if (doctor.getEmployeeId() == null || !doctor.getEmployeeId().startsWith("DT-")) {
            doctor.setEmployeeId(StaffIdentifierGenerator.generate("DT"));
            return doctorRepository.save(doctor);
        }
        return doctor;
    }
}
