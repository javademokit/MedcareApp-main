package com.example.MedcareApp.Entity.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InsuranceClaim {
    private String provider;
    private String policyLastFour;
    private String claimReference;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount = BigDecimal.ZERO;
    private String status = "SUBMITTED";
    private String decisionNotes;
    private Instant submittedAt;
    private Instant decidedAt;
}
