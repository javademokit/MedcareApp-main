package com.example.MedcareApp.Entity.emergency;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "ambulance_vehicles")
public class AmbulanceVehicle {
    @Id
    private String id = UUID.randomUUID().toString();
    private String registrationNumber;
    private String branchId;
    private String branchName;
    private String vehicleType = "BASIC";
    private String driverName;
    private String driverPhone;
    private String currentLocation;
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal locationAccuracyMeters;
    private Instant locationUpdatedAt;
    @JsonIgnore
    private String pairingCodeHash;
    private Instant pairingCodeExpiresAt;
    @JsonIgnore
    private String locationTokenHash;
    private String status = "AVAILABLE";
    private Instant updatedAt = Instant.now();
    @Version
    private Long version;
}
