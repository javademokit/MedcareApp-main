package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.staff.StaffShift;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.services.StaffShiftService;
import com.example.MedcareApp.services.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
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
    private final UserService userService;
    private final DoctorRepository doctorRepository;

    @GetMapping("/mine")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<StaffShift> getMyShifts(Principal principal) {
        return staffShiftService.getDoctorShifts(doctorStaffIds(principal));
    }

    @PostMapping("/mine/{id}/check-in")
    @PreAuthorize("hasRole('DOCTOR')")
    public StaffShift checkInMyShift(@PathVariable String id, Principal principal) {
        return staffShiftService.checkInDoctorForStaff(id, doctorStaffIds(principal));
    }

    @PostMapping("/mine/{id}/check-out")
    @PreAuthorize("hasRole('DOCTOR')")
    public StaffShift checkOutMyShift(@PathVariable String id, Principal principal) {
        return staffShiftService.checkOutDoctorForStaff(id, doctorStaffIds(principal));
    }

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

    @PostMapping("/{id}/check-in")
    public StaffShift checkInDoctor(@PathVariable String id) {
        return staffShiftService.checkInDoctor(id);
    }

    @PostMapping("/{id}/check-out")
    public StaffShift checkOutDoctor(@PathVariable String id) {
        return staffShiftService.checkOutDoctor(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(@PathVariable String id) {
        staffShiftService.deleteShift(id);
    }

    private List<String> doctorStaffIds(Principal principal) {
        user account = userService.getUserByEmail(principal.getName());
        if (account == null || !account.isActive() || !StringUtils.hasText(account.getDoctorId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No doctor profile is linked to this account");
        }
        Doctor doctor = doctorRepository.findById(account.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The linked doctor profile no longer exists"));
        List<String> staffIds = new java.util.ArrayList<>();
        if (StringUtils.hasText(doctor.getId())) staffIds.add(doctor.getId());
        if (StringUtils.hasText(doctor.getEmployeeId())) staffIds.add(doctor.getEmployeeId());
        return staffIds;
    }
}
