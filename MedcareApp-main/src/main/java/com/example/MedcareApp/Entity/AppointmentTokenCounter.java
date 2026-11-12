package com.example.MedcareApp.Entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "appointment_token_counters")
public class AppointmentTokenCounter {
    @Id
    private String id;
    private String serviceDate;
    private long lastNumber;
}
