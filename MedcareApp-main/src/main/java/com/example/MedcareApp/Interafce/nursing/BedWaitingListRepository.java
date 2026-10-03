package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.BedWaitingListEntry;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BedWaitingListRepository extends MongoRepository<BedWaitingListEntry, String> {
    List<BedWaitingListEntry> findByStatusOrderByRequestedAtAsc(String status);
}
