package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_salary_structures")
public class SalaryStructure {
    @Id
    private String id;
    private String name;
    private String appliesTo;
    private String description;
    private boolean active = true;
    private int version = 1;
    private List<ComponentLine> components = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @Data
    public static class ComponentLine {
        private String componentId;
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal percent = BigDecimal.ZERO;
        private BigDecimal units = BigDecimal.ZERO;
        private int sequence;
    }
}
