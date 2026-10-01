package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DiagnosticResultRequest {
    @NotBlank
    private String resultSummary;
}
