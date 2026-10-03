package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.BedStatusHistory;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BedStatusHistoryRepository extends MongoRepository<BedStatusHistory, String> {
    List<BedStatusHistory> findByBedIdOrderByChangedAtDesc(String bedId);
}
