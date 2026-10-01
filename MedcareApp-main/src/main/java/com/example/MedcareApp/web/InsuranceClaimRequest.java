package com.example.MedcareApp.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class InsuranceClaimRequest {
    @NotBlank private String provider;
    @NotBlank private String policyLastFour;
    @NotNull @DecimalMin("0.0") private BigDecimal requestedAmount;
}
