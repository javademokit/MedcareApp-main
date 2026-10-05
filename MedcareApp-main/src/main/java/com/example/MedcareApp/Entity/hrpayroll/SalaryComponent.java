package com.example.MedcareApp.Entity.hrpayroll;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_salary_components")
public class SalaryComponent {
    @Id
    private String id = UUID.randomUUID().toString();
    private String name;
    private String code;
    private String type;
    private String calculationType;
    private BigDecimal value = BigDecimal.ZERO;
    private String formula;
    private String baseCode = "BASIC";
    private String status = "ACTIVE";
}
