package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.Consultation;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConsultationRepository extends MongoRepository<Consultation, String> {
    List<Consultation> findAllByPatientIdOrderByCreatedAtDesc(String patientId);
    boolean existsByAppointmentId(String appointmentId);
}
