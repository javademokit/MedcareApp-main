package com.example.MedcareApp.Interafce;

import com.example.MedcareApp.Entity.staff.StaffShift;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffShiftRepository extends MongoRepository<StaffShift, String> {
    List<StaffShift> findAllByOrderByShiftDateAscStartTimeAsc();
    List<StaffShift> findAllByStaffIdAndShiftDate(String staffId, String shiftDate);
    List<StaffShift> findAllByStaffIdInAndStaffRoleIgnoreCaseOrderByShiftDateAscStartTimeAsc(
            List<String> staffIds, String staffRole);
}
