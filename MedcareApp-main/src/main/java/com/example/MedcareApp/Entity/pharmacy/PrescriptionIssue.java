package com.example.MedcareApp.Entity.pharmacy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "pharmacy_prescription_issues")
public class PrescriptionIssue {
    @Id
    private String id;
    @NotBlank
    private String prescriptionId;
    @NotBlank
    private String patientId;
    private String patientName;
    @NotBlank
    private String medicationId;
    @NotBlank
    private String medicationName;
    @NotBlank
    private String dosage;
    @Min(1)
    private int quantity;
    private String status = "PENDING";
    private String issuedBy;
    private Instant createdAt;
    private Instant issuedAt;
}
