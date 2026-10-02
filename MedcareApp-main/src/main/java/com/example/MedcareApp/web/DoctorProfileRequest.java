package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import lombok.Data;

@Data
public class DoctorProfileRequest {
    @NotBlank
    private String doctorName;

    @NotBlank
    private String doctorSpecialistName;

    private String doctorMobileNo;
    private String doctorDestination;

    @NotEmpty
    private List<@NotBlank String> doctorAvailabletime;

    @PositiveOrZero
    private double doctorfee;
}
