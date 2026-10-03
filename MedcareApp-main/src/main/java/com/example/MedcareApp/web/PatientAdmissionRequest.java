package com.example.MedcareApp.web;

import lombok.Data;

@Data
public class PatientAdmissionRequest {
    private String wardNumber;
    private String wardId;
    private String bedId;
}
