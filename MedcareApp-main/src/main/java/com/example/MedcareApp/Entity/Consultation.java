package com.example.MedcareApp.Entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "consultations")
public class Consultation {
    @Id
    private String id = UUID.randomUUID().toString();
    private String appointmentId;
    private String patientId;
    private String doctorId;
    private String doctorName;
    private String symptoms;
    private String bloodPressure;
    private String pulse;
    private String temperature;
    private String oxygenSaturation;
    private String weight;
    private String diagnosis;
    private String prescription;
    private List<String> labOrders = List.of();
    private String doctorNotes;
    private String followUpDate;
    private String status = "COMPLETED";
    private Instant createdAt = Instant.now();
}
