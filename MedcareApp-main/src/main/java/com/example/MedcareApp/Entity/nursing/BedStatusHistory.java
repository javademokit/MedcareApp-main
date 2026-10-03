package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "bed_status_history")
public class BedStatusHistory {
    @Id
    private String id = UUID.randomUUID().toString();
    private String bedId;
    private String wardId;
    private String fromStatus;
    private String toStatus;
    private String changedBy;
    private String reason;
    private Instant changedAt = Instant.now();
}
