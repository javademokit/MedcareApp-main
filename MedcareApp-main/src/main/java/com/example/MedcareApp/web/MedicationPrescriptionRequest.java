package com.example.MedcareApp.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MedicationPrescriptionRequest {
    @Data
    public static class MedicationOrder {
        @NotBlank private String medicationId;
        @NotBlank private String dose;
        @NotBlank private String route;
        @NotBlank private String frequency;
        @NotBlank private String duration;
        @Min(1) private int quantity;
        private String instructions;
    }
}
