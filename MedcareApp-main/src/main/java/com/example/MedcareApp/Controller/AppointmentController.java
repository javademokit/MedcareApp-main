package com.example.MedcareApp.Controller;


import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Interafce.AppointmentRepository;
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

    @Autowired
    private AppointmentRepository repository;

    @PostMapping
    public Appointment bookAppointment(@RequestBody Appointment appointment) {


        return repository.save(appointment);
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
        Optional<Appointment> optionalAppointment = repository.findById(id);

        if (optionalAppointment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Appointment not found"));
        }

        Appointment appointment = optionalAppointment.get();
        String newStatus = updates.get("status");  // We expect the field name to be 'status'

        if (newStatus != null) {
            appointment.setAppointmentStatus(newStatus); // Ensure this matches your field name in Appointment entity
            repository.save(appointment);
            return ResponseEntity.ok(Map.of("message", "Status updated", "status", newStatus));
        }

        return ResponseEntity.badRequest()
                .body(Map.of("message", "Missing 'status' in request body"));
    }

}
