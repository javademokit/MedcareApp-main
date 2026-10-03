package com.example.MedcareApp.Entity.nursing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "ward_rooms")
public class WardRoom {
    @Id
    private String id = UUID.randomUUID().toString();
    private String wardId;
    private String building;
    private String floor;
    @Indexed(unique = true)
    private String roomNumber;
    private String acType = "NON_AC";
    private String category = "GENERAL";
    private int bedCapacity = 1;
    private String defaultBedType = "STANDARD";
    private String genderRestriction = "ANY";
    private String status = "ACTIVE";
    private List<String> amenities = new ArrayList<>();
    private String notes;
}
