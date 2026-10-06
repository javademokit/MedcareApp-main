package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AmbulancePairRequest {
    @NotBlank private String code;
}
