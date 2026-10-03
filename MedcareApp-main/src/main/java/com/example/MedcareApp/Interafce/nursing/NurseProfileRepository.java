package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.NurseProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NurseProfileRepository extends MongoRepository<NurseProfile, String> {
    Optional<NurseProfile> findByAccountId(String accountId);
    List<NurseProfile> findByStatus(String status);
}
