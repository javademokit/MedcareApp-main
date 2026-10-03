package com.example.MedcareApp.Entity.nursing;

import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "ward_beds")
public class WardBed {
    @Id
    private String id = UUID.randomUUID().toString();
    private String wardId;
    private String bedNumber;
    private String status = "VACANT";
    private String patientId;
}
