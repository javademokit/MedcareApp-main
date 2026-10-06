package com.example.MedcareApp.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AmbulanceBookingRequest {
    @NotBlank private String patientId;
    private String branchId;
    @NotBlank private String pickupAddress;
    @NotBlank private String dropAddress;
    @NotNull @DecimalMin("0.1") private BigDecimal distanceKm;
    @NotBlank private String paymentMethod;
}
