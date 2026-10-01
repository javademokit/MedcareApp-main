package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.staff.StaffShift;
import com.example.MedcareApp.Interafce.StaffShiftRepository;
import java.util.List;
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
}
