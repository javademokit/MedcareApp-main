package com.example.MedcareApp.Entity.nursing;

import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "nurse_profiles")
public class NurseProfile {
    @Id
    private String id = UUID.randomUUID().toString();
    private String accountId;
    private String name;
    private String photoUrl;
    private String phone;
    private String email;
    private String employeeId;
    private String qualification;
    private String licenseNumber;
    private String designation;
    private String specialization;
    private String status = "ACTIVE";
}
