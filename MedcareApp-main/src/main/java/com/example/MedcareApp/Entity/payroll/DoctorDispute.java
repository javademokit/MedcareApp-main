package com.example.MedcareApp.Entity.payroll;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_doctor_disputes")
public class DoctorDispute {
    @Id
    private String id;
    private String earningId;
    private String doctorId;
    private String reason;
    private String status = "OPEN";
    private String resolution;
    private String resolvedBy;
    private Instant raisedAt = Instant.now();
    private Instant resolvedAt;
}
