package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.nursing.WardBed;
import com.example.MedcareApp.testModel.MedicalTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface DashboardSummaryDataSource {
    List<Appointment> findAppointments(LocalDate firstDay, LocalDate lastDay);
    int countOpenEmergencies();
    List<EmergencyCase> findEmergencies(Instant firstInstant, Instant nextDayInstant);
    int countTestsWithStatus(String status);
    List<MedicalTest> findTests(LocalDate firstDay, LocalDate lastDay);
    List<PatientAdmissionBucket> findActivePatientAdmissionBuckets();
    List<Ward> findWards();
    List<WardBed> findWardBeds();
    int countActiveNurses();
}
