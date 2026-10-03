package com.example.MedcareApp.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatientAccountRequest {
    @NotBlank
    private String userId;

    @NotBlank
    @Email
    private String emailId;

    private String mobileNo;

    @NotBlank
    private String patientName;

    private String patientAge;
    private String gender;
    private String patientAddress;

    @NotBlank
    @Size(min = 12)
    private String password;
}
