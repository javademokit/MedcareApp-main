package com.example.MedcareApp.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateDischargeRequest {
    @NotBlank private String patientId;
    @NotBlank private String diagnosis;
    private String dischargeSummary;
    private String attendingDoctor;
    @NotNull @DecimalMin("0.0") private BigDecimal invoiceTotal;
}
