package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.WardBed;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WardBedRepository extends MongoRepository<WardBed, String> {
    List<WardBed> findByWardId(String wardId);
    Optional<WardBed> findByWardIdAndBedNumber(String wardId, String bedNumber);
    List<WardBed> findByRoomId(String roomId);
}
