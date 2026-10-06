package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AmbulanceDispatchRequest {
    @NotBlank private String vehicleId;
    private String trackingNote;
}
