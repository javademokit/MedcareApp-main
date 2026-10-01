package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.pharmacy.MedicationItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicationRepository extends MongoRepository<MedicationItem, String> {
}
