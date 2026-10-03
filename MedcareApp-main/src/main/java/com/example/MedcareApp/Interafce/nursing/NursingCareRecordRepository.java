package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.NursingCareRecord;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NursingCareRecordRepository extends MongoRepository<NursingCareRecord, String> {
    List<NursingCareRecord> findByPatientIdOrderByRecordedAtDesc(String patientId);
    List<NursingCareRecord> findByNurseIdOrderByRecordedAtDesc(String nurseId);
    List<NursingCareRecord> findAllByOrderByRecordedAtDesc();
}
