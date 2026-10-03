package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PatientAssignmentRepository extends MongoRepository<PatientAssignment, String> {
    List<PatientAssignment> findByPatientIdAndStatus(String patientId, String status);
    List<PatientAssignment> findByNurseIdAndStatus(String nurseId, String status);
    List<PatientAssignment> findByWardIdAndStatus(String wardId, String status);
}
