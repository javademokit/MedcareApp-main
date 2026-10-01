package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Interafce.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    // Fetch all appointments for the report
    @GetMapping("/appointments")
    public List<Appointment> getAllAppointmentsReport() {
        return appointmentRepository.findAll();
    }
}
