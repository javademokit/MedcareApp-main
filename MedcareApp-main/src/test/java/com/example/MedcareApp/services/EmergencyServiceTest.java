package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import com.example.MedcareApp.Interafce.EmergencyCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {
    @Mock private EmergencyCaseRepository repository;
    @InjectMocks private EmergencyService service;

    @Test
    void criticalSeverityRaisesAutomaticAlertFlag() {
        when(repository.save(any(EmergencyCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EmergencyCase emergencyCase = new EmergencyCase();
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
        EmergencyCase emergencyCase = new EmergencyCase();
        emergencyCase.setPatientName("Demo patient");
        emergencyCase.setComplaint("Requires transport");
        emergencyCase.setSeverity("HIGH");
        emergencyCase.setAmbulanceRequired(true);

        EmergencyCase saved = service.createCase(emergencyCase);

        assertTrue(saved.isAmbulanceRequired());
        assertEquals("REQUESTED", saved.getAmbulanceStatus());
    }
}
