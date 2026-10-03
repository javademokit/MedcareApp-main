package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "nurse_handovers")
public class NurseHandover {
    @Id
    private String id = UUID.randomUUID().toString();
    private String patientId;
    private String fromNurseId;
    private String toNurseId;
    private String shift;
    private String note;
    private String status = "PENDING";
    private String createdBy;
    private Instant createdAt = Instant.now();
    private Instant acknowledgedAt;
}
