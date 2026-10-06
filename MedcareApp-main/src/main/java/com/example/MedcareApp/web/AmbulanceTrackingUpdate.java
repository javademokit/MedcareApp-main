package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AmbulanceTrackingUpdate {
    @NotBlank private String status;
    private String currentLocation;
    private String trackingNote;
}
