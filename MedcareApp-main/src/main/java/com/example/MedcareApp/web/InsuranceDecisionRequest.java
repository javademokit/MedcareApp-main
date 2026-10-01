package com.example.MedcareApp.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class InsuranceDecisionRequest {
    @NotBlank private String status;
    @NotNull @DecimalMin("0.0") private BigDecimal approvedAmount;
    private String claimReference;
    private String decisionNotes;
}
