package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import com.example.MedcareApp.Interafce.EmergencyCaseRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {
    @Mock private EmergencyCaseRepository repository;
    @Mock private PatientRepository patientRepository;
    @InjectMocks private EmergencyService service;

    @Test
    void criticalSeverityRaisesAutomaticAlertFlag() {
        when(repository.save(any(EmergencyCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.findAllByPatientId("PT-1")).thenReturn(List.of(patient("PT-1")));
        EmergencyCase emergencyCase = new EmergencyCase();
        emergencyCase.setPatientId("PT-1");
        emergencyCase.setPatientName("Demo patient");
        emergencyCase.setComplaint("Needs immediate assessment");
        emergencyCase.setSeverity("CRITICAL");

        EmergencyCase saved = service.createCase(emergencyCase);

        assertTrue(saved.isCriticalAlert());
        assertTrue(saved.getStatus().equals("WAITING_FOR_TRIAGE"));
    }

    @Test
    void ambulanceRequiredArrivalStartsInRequestedState() {
        when(repository.save(any(EmergencyCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.findAllByPatientId("PT-1")).thenReturn(List.of(patient("PT-1")));
        EmergencyCase emergencyCase = new EmergencyCase();
        emergencyCase.setPatientId("PT-1");
        emergencyCase.setPatientName("Demo patient");
        emergencyCase.setComplaint("Requires transport");
        emergencyCase.setSeverity("HIGH");
        emergencyCase.setAmbulanceRequired(true);

        EmergencyCase saved = service.createCase(emergencyCase);

        assertTrue(saved.isAmbulanceRequired());
        assertEquals("REQUESTED", saved.getAmbulanceStatus());
    }

    private Patient patient(String id) {
        Patient patient = new Patient();
        patient.setPatientId(id);
        patient.setPatientName("Demo patient");
        return patient;
    }
}
