package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.NurseHandover;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NurseHandoverRepository extends MongoRepository<NurseHandover, String> {
    List<NurseHandover> findByToNurseIdAndStatusOrderByCreatedAtDesc(String toNurseId, String status);
}
