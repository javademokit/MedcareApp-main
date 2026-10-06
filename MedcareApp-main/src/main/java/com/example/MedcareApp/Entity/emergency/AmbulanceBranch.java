package com.example.MedcareApp.Entity.emergency;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "ambulance_branches")
public class AmbulanceBranch {
    @Id
    private String id = UUID.randomUUID().toString();
    private String name;
    private String address;
    private Instant createdAt = Instant.now();
}
