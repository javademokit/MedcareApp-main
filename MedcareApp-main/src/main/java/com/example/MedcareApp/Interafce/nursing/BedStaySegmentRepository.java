package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.BedStaySegment;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BedStaySegmentRepository extends MongoRepository<BedStaySegment, String> {
    List<BedStaySegment> findByPatientIdOrderByFromTimeDesc(String patientId);
    List<BedStaySegment> findByStatus(String status);
}
