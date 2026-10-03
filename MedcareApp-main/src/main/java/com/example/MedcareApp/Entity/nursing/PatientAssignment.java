package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "patient_assignments")
public class PatientAssignment {
    @Id
    private String id = UUID.randomUUID().toString();
    private String patientId;
    private String nurseId;
    private String wardId;
    private String bedId;
    private String shift;
    private String role = "PRIMARY";
    private String status = "ACTIVE";
    private String assignedBy;
    private String source = "MANUAL";
    private Instant fromTime = Instant.now();
    private Instant toTime;
}
