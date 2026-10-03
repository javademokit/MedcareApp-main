package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "bed_waiting_list")
public class BedWaitingListEntry {
    @Id
    private String id = UUID.randomUUID().toString();
    private String patientId;
    private String patientName;
    private String wardId;
    private String preferredAcType;
    private String preferredCategory;
    private String priority = "NORMAL";
    private String status = "WAITING";
    private Instant requestedAt = Instant.now();
    private String createdBy;
}
