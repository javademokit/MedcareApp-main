package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.staff.StaffShift;
import com.example.MedcareApp.services.StaffShiftService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/shifts")
@RequiredArgsConstructor
public class StaffShiftController {
    private final StaffShiftService staffShiftService;

    @GetMapping
    public List<StaffShift> getShifts() {
        return staffShiftService.getShifts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffShift createShift(@Valid @RequestBody StaffShift shift) {
        return staffShiftService.createShift(shift);
    }

    @PutMapping("/{id}")
    public StaffShift updateShift(@PathVariable String id, @Valid @RequestBody StaffShift shift) {
        return staffShiftService.updateShift(id, shift);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(@PathVariable String id) {
        staffShiftService.deleteShift(id);
    }
}
