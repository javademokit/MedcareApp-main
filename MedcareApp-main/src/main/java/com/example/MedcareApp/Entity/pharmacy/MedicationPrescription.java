package com.example.MedcareApp.Entity.pharmacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "pharmacy_medication_prescriptions")
public class MedicationPrescription {
    @Id
    private String id;
    private String consultationId;
    private String appointmentId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private String diagnosis;
    private String status = "PENDING";
    private List<MedicationLine> medications = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant dispensedAt;
    private String dispensedBy;

    @Data
    public static class MedicationLine {
        private String medicationId;
        private String name;
        private String department;
        private String strength;
        private String dosageForm;
        private String dose;
        private String route;
        private String frequency;
        private String duration;
        private int quantity;
        private String instructions;
        private String status = "PENDING";
    }
}
