package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.emergency.AmbulanceBranch;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AmbulanceBranchRepository extends MongoRepository<AmbulanceBranch, String> {
    List<AmbulanceBranch> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
