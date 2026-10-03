package com.example.MedcareApp.Interafce.nursing;

import com.example.MedcareApp.Entity.nursing.NurseShiftRoster;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NurseShiftRosterRepository extends MongoRepository<NurseShiftRoster, String> {
    List<NurseShiftRoster> findByWardIdAndStatus(String wardId, String status);
    List<NurseShiftRoster> findByNurseIdAndStatus(String nurseId, String status);
}
