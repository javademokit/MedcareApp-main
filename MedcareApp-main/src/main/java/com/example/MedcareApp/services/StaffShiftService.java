package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.staff.StaffShift;
import com.example.MedcareApp.Interafce.StaffShiftRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class StaffShiftService {
    private final StaffShiftRepository repository;

    public List<StaffShift> getShifts() {
        return repository.findAllByOrderByShiftDateAscStartTimeAsc();
    }

    public List<StaffShift> getDoctorShifts(List<String> staffIds) {
        if (staffIds.isEmpty()) return List.of();
        return repository.findAllByStaffIdInAndStaffRoleIgnoreCaseOrderByShiftDateAscStartTimeAsc(
                staffIds, "Doctor");
    }

    public StaffShift checkInDoctorForStaff(String id, List<String> staffIds) {
        return updateDoctorAttendance(id, staffIds, true);
    }

    public StaffShift checkOutDoctorForStaff(String id, List<String> staffIds) {
        return updateDoctorAttendance(id, staffIds, false);
    }

    private StaffShift updateDoctorAttendance(String id, List<String> staffIds, boolean checkIn) {
        StaffShift shift = getDoctorShiftForToday(id);
        if (!staffIds.contains(shift.getStaffId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This shift is not assigned to your doctor profile");
        }
        if (checkIn) {
            if (!"SCHEDULED".equals(shift.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only a scheduled doctor shift can be checked in");
            }
            shift.setStatus("ON_DUTY");
            shift.setCheckInAt(Instant.now());
        } else {
            if (!"ON_DUTY".equals(shift.getStatus()) || shift.getCheckInAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Doctor must be checked in before checking out");
            }
            shift.setStatus("COMPLETED");
            shift.setCheckOutAt(Instant.now());
        }
        return repository.save(shift);
    }

    public StaffShift createShift(StaffShift shift) {
        validateShift(shift, null);
        shift.setStatus("SCHEDULED");
        shift.setCreatedAt(Instant.now());
        return repository.save(shift);
    }

    public StaffShift updateShift(String id, StaffShift update) {
        StaffShift existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        update.setId(id);
        if ("CANCELLED".equalsIgnoreCase(update.getStatus())) {
            if (!"SCHEDULED".equals(existing.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only scheduled shifts can be cancelled");
            }
            update.setStatus("CANCELLED");
        } else {
            update.setStatus(existing.getStatus());
        }
        update.setCreatedAt(existing.getCreatedAt());
        update.setCheckInAt(existing.getCheckInAt());
        update.setCheckOutAt(existing.getCheckOutAt());
        validateShift(update, id);
        return repository.save(update);
    }

    public StaffShift checkInDoctor(String id) {
        StaffShift shift = getDoctorShiftForToday(id);
        if (!"SCHEDULED".equals(shift.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a scheduled doctor shift can be checked in");
        }
        shift.setStatus("ON_DUTY");
        shift.setCheckInAt(Instant.now());
        return repository.save(shift);
    }

    public StaffShift checkOutDoctor(String id) {
        StaffShift shift = getDoctorShiftForToday(id);
        if (!"ON_DUTY".equals(shift.getStatus()) || shift.getCheckInAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Doctor must be checked in before checking out");
        }
        shift.setStatus("COMPLETED");
        shift.setCheckOutAt(Instant.now());
        return repository.save(shift);
    }

    private StaffShift getDoctorShiftForToday(String id) {
        StaffShift shift = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        if (!"DOCTOR".equalsIgnoreCase(shift.getStaffRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Attendance action is only available for doctor shifts");
        }
        if (!LocalDate.now().toString().equals(shift.getShiftDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Doctor attendance can only be recorded on the scheduled shift date");
        }
        return shift;
    }

    public void deleteShift(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found");
        }
        repository.deleteById(id);
    }

    private void validateShift(StaffShift shift, String currentId) {
        try {
            LocalDate.parse(shift.getShiftDate());
            LocalTime start = LocalTime.parse(shift.getStartTime());
            LocalTime end = LocalTime.parse(shift.getEndTime());
            if (!start.isBefore(end)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shift end time must be after start time");
            }
            boolean conflict = repository.findAllByStaffIdAndShiftDate(shift.getStaffId(), shift.getShiftDate())
                    .stream()
                    .filter(existing -> currentId == null || !currentId.equals(existing.getId()))
                    .anyMatch(existing -> start.isBefore(LocalTime.parse(existing.getEndTime()))
                            && end.isAfter(LocalTime.parse(existing.getStartTime())));
            if (conflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This staff member already has an overlapping shift");
            }
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use YYYY-MM-DD for the date and HH:mm for shift times");
        }
    }
}
