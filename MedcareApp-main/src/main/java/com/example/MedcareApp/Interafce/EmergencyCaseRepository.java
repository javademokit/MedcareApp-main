package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmergencyCaseRepository extends MongoRepository<EmergencyCase, String> {
    List<EmergencyCase> findAllByOrderByCreatedAtDesc();
}
