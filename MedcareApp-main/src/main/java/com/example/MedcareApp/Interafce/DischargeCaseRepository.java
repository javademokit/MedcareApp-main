package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.DischargeCase;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DischargeCaseRepository extends MongoRepository<DischargeCase, String> {
    List<DischargeCase> findAllByOrderByCreatedAtDesc();
    List<DischargeCase> findAllByPatientIdAndStatusNot(String patientId, String status);
}
