package com.example.MedcareApp.Controller;


import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Interafce.AppointmentRepository;
import com.example.MedcareApp.services.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/appointments1")
public class AppointmentController {

    private final AppointmentRepository repository;
    private final AppointmentService appointmentService;

    @Autowired
    public AppointmentController(AppointmentRepository repository, AppointmentService appointmentService) {
        this.repository = repository;
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<Appointment> bookAppointment(@RequestBody Appointment appointment) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.bookAppointment(appointment));
    }
    @GetMapping
    public List<Appointment> getAllAppointments() {
        return repository.findAll();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> updates
    ) {
        String newStatus = updates.get("status");
        Appointment appointment = appointmentService.updateStatus(id, newStatus);
        return ResponseEntity.ok(Map.of(
                "message", "Status updated",
                "status", appointment.getAppointmentStatus()));
    }

}
