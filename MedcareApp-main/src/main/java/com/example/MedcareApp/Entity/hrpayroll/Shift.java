package com.example.MedcareApp.Entity.hrpayroll;

import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_shifts")
public class Shift {
    @Id
    private String id = UUID.randomUUID().toString();
    private String name;
    private String startTime;
    private String endTime;
    private int breakMinutes;
    private String status = "ACTIVE";
}
