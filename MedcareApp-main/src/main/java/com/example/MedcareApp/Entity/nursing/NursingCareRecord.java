package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "nursing_care_records")
public class NursingCareRecord {
    @Id
    private String id = UUID.randomUUID().toString();
    private String patientId;
    private String nurseId;
    private String type;
    private String description;
    private String value;
    private String dueAt;
    private String status = "PENDING";
    private String note;
    private Instant recordedAt = Instant.now();
}
