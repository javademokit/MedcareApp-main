package com.example.MedcareApp.Entity.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_statutory_config")
public class StatutoryConfig {
    @Id
    private String id;
    private String name;
    private String appliesTo;
    private BigDecimal rate;
    private BigDecimal ceiling;
    private LocalDate effectiveFrom;
    private Instant createdAt = Instant.now();
}
