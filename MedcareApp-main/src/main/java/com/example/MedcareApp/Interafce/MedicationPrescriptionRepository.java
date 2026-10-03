package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.pharmacy.MedicationPrescription;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicationPrescriptionRepository extends MongoRepository<MedicationPrescription, String> {
    List<MedicationPrescription> findAllByOrderByCreatedAtDesc();
}
