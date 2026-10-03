package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "bed_stay_segments")
public class BedStaySegment {
    @Id
    private String id = UUID.randomUUID().toString();
    private String patientId;
    private String wardId;
    private String roomId;
    private String bedId;
    private Instant fromTime = Instant.now();
    private Instant toTime;
    private String reason;
    private String allocatedBy;
    private String status = "ACTIVE";
}
