package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.Patient;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PatientRepository extends MongoRepository<Patient, String> {
	List<Patient> findAllByPatientId(String patientId);
}
