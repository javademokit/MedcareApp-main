package com.example.payroll.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "employee_profile_pictures")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeProfilePicture {
    @Id
    private Long employeeId;
    private String contentType;
    private byte[] content;
    private Instant uploadedAt;
}
