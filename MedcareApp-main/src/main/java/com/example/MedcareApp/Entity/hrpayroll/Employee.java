package com.example.MedcareApp.Entity.hrpayroll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_employees")
public class Employee {
    @Id
    private String id = UUID.randomUUID().toString();
    private String employeeCode;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String mobile;
    private String email;
    private String address;
    private String emergencyContact;
    private String employeeType;
    private String departmentId;
    private String departmentName;
    private String designationId;
    private String doctorProfileId;
    private LocalDate joiningDate;
    private String employmentType;
    private String managerId;
    private String location;
    private String status = "ONBOARDING";
    private String shiftId;
    private Map<String, Object> professionalInfo = new LinkedHashMap<>();
    private List<String> documentLinks = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getFullName() {
        return String.join(" ", firstName == null ? "" : firstName,
                lastName == null ? "" : lastName).trim();
    }
}
