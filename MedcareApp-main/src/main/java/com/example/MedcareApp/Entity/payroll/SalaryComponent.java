package com.example.MedcareApp.Entity.payroll;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_salary_components")
public class SalaryComponent {
    @Id
    private String id;
    private String name;
    private String code;
    private String type;
    private String calculation;
    private String formula;
    private boolean taxable;
    private String appliesTo;
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
