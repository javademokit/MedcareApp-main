package com.example.MedcareApp.Controller;


import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.services.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments1")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Autowired
    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<Appointment> bookAppointment(@RequestBody Appointment appointment) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.bookAppointment(appointment));
    }
    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentService.getAllAppointments();
    }

    @GetMapping("/availability")
    public List<String> getAvailability(
            @RequestParam String doctorId,
            @RequestParam String date) {
        return appointmentService.getAvailableTimes(doctorId, date);
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleAppointmentError(ResponseStatusException exception) {
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? "The appointment request could not be completed."
                : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", message));
    }

}
