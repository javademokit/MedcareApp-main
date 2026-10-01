package com.example.MedcareApp.Entity.emergency;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "emergency_cases")
public class EmergencyCase {
    @Id
    private String id;
    private String patientId;
    @NotBlank
    private String patientName;
    @NotBlank
    private String complaint;
    @NotBlank
    private String severity;
    private String status = "WAITING_FOR_TRIAGE";
    private String assignedClinician;
    private boolean ambulanceRequired;
    private String ambulanceStatus = "NOT_REQUIRED";
    private String transferStatus = "NOT_REQUIRED";
    private String transferDestination;
    private String triageNotes;
    private boolean criticalAlert;
    private Instant createdAt;
    private Instant updatedAt;
}
