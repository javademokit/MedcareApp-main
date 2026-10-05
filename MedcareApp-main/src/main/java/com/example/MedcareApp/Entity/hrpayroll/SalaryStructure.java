package com.example.MedcareApp.Entity.hrpayroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_salary_structures")
public class SalaryStructure {
    @Id
    private String id = UUID.randomUUID().toString();
    private String employeeId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status = "ACTIVE";
    private List<ComponentLine> components = new ArrayList<>();
    private Instant createdAt = Instant.now();

    @Data
    public static class ComponentLine {
        private String componentId;
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal units = BigDecimal.ONE;
    }
}
