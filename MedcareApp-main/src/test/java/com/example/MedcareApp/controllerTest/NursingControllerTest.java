package com.example.MedcareApp.controllerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Controller.NursingController;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.NursingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NursingControllerTest {
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;
    @Mock private NursingService nursingService;
    @InjectMocks private NursingController controller;

    @Test
    void assignsPrimaryNurseToPatientAndReturnsUpdatedPatient() {
        Patient patient = new Patient();
        patient.setPatientId("PT-100");
        patient.setPatientName("A Patient");
        patient.setPatientAdmitdate("2026-10-03");
        PatientAssignment assignment = new PatientAssignment();
        assignment.setPatientId("PT-100");
        assignment.setNurseId("nurse-account-1");
        when(patientRepository.findAllByPatientId("PT-100")).thenReturn(List.of(patient));
        when(nursingService.assignPatient("PT-100", "nurse-account-1", "PRIMARY", null, "admin@example.test"))
                .thenReturn(assignment);

        Patient updated = controller.assignPrimaryNurse(
                "PT-100", new NursingController.NurseAssignmentRequest("nurse-account-1"),
                () -> "admin@example.test").getBody();

        assertEquals("PT-100", updated.getPatientId());
        verify(nursingService).assignPatient("PT-100", "nurse-account-1", "PRIMARY", null, "admin@example.test");
    }

    @Test
    void rejectsDuplicatePatientIdsWhenReturningAssignment() {
        Patient patient = new Patient();
        patient.setPatientId("PT-100");
        Patient duplicate = new Patient();
        duplicate.setPatientId("PT-100");
        when(patientRepository.findAllByPatientId("PT-100")).thenReturn(List.of(patient, duplicate));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                controller.assignPrimaryNurse("PT-100",
                        new NursingController.NurseAssignmentRequest("nurse-account-1"),
                        () -> "admin@example.test"));

        assertEquals(409, error.getStatusCode().value());
    }
}
