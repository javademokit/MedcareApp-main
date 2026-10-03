package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "nurse_shift_roster")
public class NurseShiftRoster {
    @Id
    private String id = UUID.randomUUID().toString();
    private String nurseId;
    private String wardId;
    private String shift;
    private String startDate;
    private String endDate;
    private String status = "SCHEDULED";
    private String createdBy;
    private Instant createdAt = Instant.now();
}
