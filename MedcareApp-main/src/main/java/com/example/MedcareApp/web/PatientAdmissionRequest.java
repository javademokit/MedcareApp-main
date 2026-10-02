package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatientAdmissionRequest {
    @NotBlank
    private String wardNumber;
}
