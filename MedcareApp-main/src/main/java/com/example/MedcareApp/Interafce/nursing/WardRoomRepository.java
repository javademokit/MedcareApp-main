package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.WardRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WardRoomRepository extends MongoRepository<WardRoom, String> {
    List<WardRoom> findByWardId(String wardId);
    Optional<WardRoom> findByRoomNumberIgnoreCase(String roomNumber);
}
