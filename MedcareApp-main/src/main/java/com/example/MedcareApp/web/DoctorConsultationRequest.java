package com.example.MedcareApp.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    private List<@NotBlank String> labOrders = List.of();
    @Valid
    private List<MedicationPrescriptionRequest.MedicationOrder> medicationOrders = List.of();
    private String doctorNotes;
    private String followUpDate;
}
