package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.staff.StaffShift;
import com.example.MedcareApp.Interafce.StaffShiftRepository;
import java.time.LocalDate;
import java.time.LocalTime;
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

    public StaffShift createShift(StaffShift shift) {
        validateShift(shift, null);
        shift.setStatus("SCHEDULED");
        return repository.save(shift);
    }

    public StaffShift updateShift(String id, StaffShift update) {
        StaffShift existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        update.setId(id);
        if (update.getStatus() == null || update.getStatus().isBlank()) update.setStatus(existing.getStatus());
        validateShift(update, id);
        return repository.save(update);
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
