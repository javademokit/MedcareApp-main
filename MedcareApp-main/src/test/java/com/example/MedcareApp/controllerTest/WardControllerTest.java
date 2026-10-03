package com.example.MedcareApp.controllerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Controller.WardController;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.PatientRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WardControllerTest {
    @Mock private PatientRepository patientRepository;
    @InjectMocks private WardController controller;

    @Test
    void reportsOnlyCurrentAdmissionsAsOccupied() {
        Patient admitted = new Patient();
        admitted.setPatientId("PT-100");
        admitted.setPatientName("Admitted Patient");
        admitted.setPatientAdmitdate("2026-10-03");
        admitted.setPatientWardnum("Ward 2");
        admitted.setPatientNurseassign("nurse-one");

        Patient discharged = new Patient();
        discharged.setPatientId("PT-200");
        discharged.setPatientAdmitdate("2026-10-01");
        discharged.setPatientDischargedate("2026-10-02");

        when(patientRepository.findAll()).thenReturn(List.of(admitted, discharged));

        List<WardController.WardOccupancy> result = controller.getWardOccupancy();

        assertEquals(1, result.size());
        assertEquals("Ward 2", result.get(0).name());
        assertEquals("PT-100", result.get(0).patientId());
        assertEquals("nurse-one", result.get(0).assignedNurse());
    }
}
