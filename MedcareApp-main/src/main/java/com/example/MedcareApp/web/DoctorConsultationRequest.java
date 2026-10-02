package com.example.MedcareApp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class DoctorConsultationRequest {
    @NotBlank
    private String appointmentId;

    private String symptoms;
    private String bloodPressure;
    private String pulse;
    private String temperature;
    private String oxygenSaturation;
    private String weight;

    @NotBlank
    private String diagnosis;

    private String prescription;
    private List<@jakarta.validation.constraints.NotBlank String> labOrders = List.of();
    private String doctorNotes;
    private String followUpDate;
}
