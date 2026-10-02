package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.example.MedcareApp.Entity.staff.StaffShift;
import com.example.MedcareApp.Interafce.StaffShiftRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StaffShiftServiceTest {
    @Mock private StaffShiftRepository repository;
    @InjectMocks private StaffShiftService service;

    @Test
    void rejectsOverlappingShiftForSameStaffAndDate() {
        StaffShift existing = new StaffShift();
        existing.setId("shift-1");
        existing.setStaffId("staff-1");
        existing.setShiftDate("2026-10-01");
        existing.setStartTime("08:00");
        existing.setEndTime("16:00");
        when(repository.findAllByStaffIdAndShiftDate("staff-1", "2026-10-01")).thenReturn(List.of(existing));

        StaffShift overlapping = new StaffShift();
        overlapping.setStaffId("staff-1");
        overlapping.setShiftDate("2026-10-01");
        overlapping.setStartTime("15:30");
        overlapping.setEndTime("18:00");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.createShift(overlapping));

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).save(any(StaffShift.class));
    }

    @Test
    void doctorShiftCanBeCheckedInAndOutAndRecordsTimestamps() {
        StaffShift shift = todaysDoctorShift();
        when(repository.findById("shift-1")).thenReturn(Optional.of(shift));
        when(repository.save(any(StaffShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffShift checkedIn = service.checkInDoctor("shift-1");
        assertEquals("ON_DUTY", checkedIn.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(checkedIn.getCheckInAt());

        StaffShift checkedOut = service.checkOutDoctor("shift-1");
        assertEquals("COMPLETED", checkedOut.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(checkedOut.getCheckOutAt());
        verify(repository, org.mockito.Mockito.times(2)).save(shift);
    }

    @Test
    void doctorCannotCheckInTwice() {
        StaffShift shift = todaysDoctorShift();
        shift.setStatus("ON_DUTY");
        when(repository.findById("shift-1")).thenReturn(Optional.of(shift));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.checkInDoctor("shift-1"));

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).save(any(StaffShift.class));
    }

    @Test
    void nonDoctorShiftCannotUseDoctorAttendanceActions() {
        StaffShift shift = todaysDoctorShift();
        shift.setStaffRole("Nurse");
        when(repository.findById("shift-1")).thenReturn(Optional.of(shift));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.checkInDoctor("shift-1"));

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).save(any(StaffShift.class));
    }

    private StaffShift todaysDoctorShift() {
        StaffShift shift = new StaffShift();
        shift.setId("shift-1");
        shift.setStaffId("doctor-1");
        shift.setStaffName("Dr Example");
        shift.setStaffRole("Doctor");
        shift.setDepartment("General Medicine");
        shift.setShiftDate(java.time.LocalDate.now().toString());
        shift.setStartTime("08:00");
        shift.setEndTime("16:00");
        shift.setStatus("SCHEDULED");
        return shift;
    }
}
