package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AmbulanceVehicleRequest {
    @NotBlank private String registrationNumber;
    private String branchId;
    @NotBlank private String vehicleType;
    @NotBlank private String driverName;
    @NotBlank private String driverPhone;
    private String status;
}
