package com.example.MedcareApp.Entity.nursing;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "nurse_shift_swaps")
public class NurseShiftSwap {
    @Id
    private String id = UUID.randomUUID().toString();
    private String fromNurseId;
    private String toNurseId;
    private String wardId;
    private String shift;
    private String date;
    private String note;
    private String status = "PENDING";
    private String requestedBy;
    private Instant requestedAt = Instant.now();
    private String reviewedBy;
    private Instant reviewedAt;
    private String decisionNote;
}
