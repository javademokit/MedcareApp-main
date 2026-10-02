package com.example.MedcareApp.Entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "appointment_slot_reservations")
public class AppointmentSlotReservation {
    @Id
    private String id;
    private String appointmentId;
    private Instant reservedAt;

    public AppointmentSlotReservation() {}

    public AppointmentSlotReservation(String id, String appointmentId, Instant reservedAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.reservedAt = reservedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }
    public Instant getReservedAt() { return reservedAt; }
    public void setReservedAt(Instant reservedAt) { this.reservedAt = reservedAt; }
}
