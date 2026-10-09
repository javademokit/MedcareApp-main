package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.nursing.WardBed;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardSummaryServiceTest {
    private final DashboardSummaryDataSource dataSource =
            org.mockito.Mockito.mock(DashboardSummaryDataSource.class);
    private final DashboardSummaryService service = new DashboardSummaryService(dataSource);

    @Test
    void returnsOnlyAggregateValuesAndDoesNotExposePatientFields() {
        String today = LocalDate.now().toString();
        Appointment appointment = new Appointment();
        appointment.setDate(today);
        appointment.setTime("10:00 AM");
        appointment.setAppointmentStatus("confirmed");
        when(dataSource.findAppointments(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(appointment));
        when(dataSource.findActivePatientAdmissionBuckets())
                .thenReturn(List.of(new PatientAdmissionBucket(today, "Ward A", 1)));
        when(dataSource.findWards()).thenReturn(List.of());
        when(dataSource.findWardBeds()).thenReturn(List.of());
        when(dataSource.countActiveNurses()).thenReturn(0);
        when(dataSource.countOpenEmergencies()).thenReturn(0);
        when(dataSource.findEmergencies(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(dataSource.findTests(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(dataSource.countTestsWithStatus("RESULT_READY")).thenReturn(1);
        when(dataSource.countTestsWithStatus("ORDERED")).thenReturn(1);
        when(dataSource.countTestsWithStatus("IN_PROGRESS")).thenReturn(1);

        var summary = service.getSummary();

        assertEquals(1, summary.activeAdmissions());
        assertEquals(1, summary.wardData().get(0).patients());
        assertEquals(0, summary.totalBeds());
        assertEquals(0, summary.totalNurses());
        var todayActivity = summary.dailyActivity().get(summary.dailyActivity().size() - 1);
        assertEquals(1, todayActivity.visits());
        assertEquals(1, todayActivity.admissions());
        assertEquals(1, summary.hourlyActivity().get(10).visits());
    }

    @Test
    void summarizesRegisteredBedsByWardAndCountsActiveNurseEmployees() {
        Ward ward = new Ward();
        ward.setId("ward-1");
        ward.setName("Medical Ward");

        WardBed vacantBed = bed("ward-1", "VACANT");
        WardBed occupiedBed = bed("ward-1", "OCCUPIED");
        WardBed blockedBed = bed("ward-1", "BLOCKED");
        when(dataSource.findWards()).thenReturn(List.of(ward));
        when(dataSource.findWardBeds()).thenReturn(List.of(vacantBed, occupiedBed, blockedBed));
        when(dataSource.findActivePatientAdmissionBuckets())
                .thenReturn(List.of(new PatientAdmissionBucket(LocalDate.now().toString(), "Medical Ward", 1)));
        when(dataSource.countActiveNurses()).thenReturn(6);

        var summary = service.getSummary();

        assertEquals(3, summary.totalBeds());
        assertEquals(1, summary.availableBeds());
        assertEquals(1, summary.occupiedBeds());
        assertEquals(6, summary.totalNurses());
        assertEquals("Medical Ward", summary.wardData().get(0).unit());
        assertEquals(3, summary.wardData().get(0).totalBeds());
        assertEquals(1, summary.wardData().get(0).availableBeds());
        assertEquals(1, summary.wardData().get(0).occupiedBeds());
        assertEquals(1, summary.wardData().get(0).patients());
    }

    private static WardBed bed(String wardId, String status) {
        WardBed bed = new WardBed();
        bed.setWardId(wardId);
        bed.setStatus(status);
        return bed;
    }
}
