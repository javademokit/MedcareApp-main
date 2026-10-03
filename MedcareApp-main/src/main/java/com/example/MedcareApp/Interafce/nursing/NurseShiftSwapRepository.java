package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.NurseShiftSwap;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NurseShiftSwapRepository extends MongoRepository<NurseShiftSwap, String> {
    List<NurseShiftSwap> findAllByOrderByRequestedAtDesc();
}
