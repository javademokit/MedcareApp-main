package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AmbulanceBranchRequest {
    @NotBlank private String name;
    private String address;
}
