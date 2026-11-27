package com.example.payroll.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDate;

@Document(collection = "employees")
@Getter @Setter @NoArgsConstructor
public class Employee {
    @Id
    private Long id;
    @Indexed(unique = true)
    private String code;
    private String name;
    private String department;
    private String role;
    private BigDecimal monthlyGross;
    private String bank;
    private String bankAccountNumber;
    private String ifscCode;
    private String bankBranchName;
    private String bankCity;
    private LocalDate joiningDate;
    private String employmentType;
    private String workEmail;
    private String phone;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String registrationCouncil;
    private String professionalRegistrationNo;
    private LocalDate registrationExpiryDate;
    private boolean identityVerified;
    private boolean professionalCredentialVerified;
    private boolean contractSigned;
    private boolean orientationComplete;
    private boolean systemAccessProvisioned;
    @DBRef
    private Structure structure;
}
