package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.emergency.AmbulanceVehicle;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AmbulanceVehicleRepository extends MongoRepository<AmbulanceVehicle, String> {
    List<AmbulanceVehicle> findAllByOrderByRegistrationNumberAsc();
    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);
    boolean existsByRegistrationNumberIgnoreCaseAndIdNot(String registrationNumber, String id);
    java.util.Optional<AmbulanceVehicle> findByPairingCodeHash(String pairingCodeHash);
    java.util.Optional<AmbulanceVehicle> findByLocationTokenHash(String locationTokenHash);
}
