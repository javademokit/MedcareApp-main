package com.example.MedcareApp.Entity.nursing;

import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "wards")
public class Ward {
    @Id
    private String id = UUID.randomUUID().toString();
    private String name;
    private String type;
    private int maxPatientsPerNurse;
    private int minimumNursesPerShift;
}
