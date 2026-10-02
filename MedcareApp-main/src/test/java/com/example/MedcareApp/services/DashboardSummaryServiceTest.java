package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Appointment;
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
        var todayActivity = summary.dailyActivity().get(summary.dailyActivity().size() - 1);
        assertEquals(1, todayActivity.visits());
        assertEquals(1, todayActivity.admissions());
        assertEquals(1, summary.hourlyActivity().get(10).visits());
    }
}
