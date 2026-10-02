package com.example.MedcareApp.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

@Data
public class StaffAccountRequest {
    @NotBlank
    private String userId;

    @NotBlank
    @Email
    private String emailId;

    private String mobileNo;

    private String doctorId;

    @Valid
    private DoctorProfileRequest doctorProfile;

    @NotBlank
    @Size(min = 12)
    private String password;

    @NotEmpty
    private List<@NotBlank String> roles;
}
