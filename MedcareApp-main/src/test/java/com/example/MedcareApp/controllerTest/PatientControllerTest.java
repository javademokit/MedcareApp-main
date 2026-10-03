package com.example.MedcareApp.controllerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.example.MedcareApp.Controller.PatientController;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.services.NursingService;
import com.example.MedcareApp.web.PatientAdmissionRequest;
import java.time.LocalDate;
import java.util.List;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PatientControllerTest {
    @Mock private PatientRepository patientRepository;
    @Mock private NursingService nursingService;
    @InjectMocks private PatientController controller;

    @Test
    void admissionUpdatesTheExistingPatientInsteadOfCreatingAnotherRecord() {
        Patient patient = new Patient();
        patient.setPatientId("PT-1");
        patient.setPatientName("A Patient");
        PatientAdmissionRequest request = new PatientAdmissionRequest();
        request.setWardId("ward-1");
        request.setBedId("bed-1");
        when(nursingService.admitPatient("PT-1", "ward-1", "bed-1", "admin@example.test"))
                .thenAnswer(invocation -> {
                    patient.setPatientAdmitdate(LocalDate.now().toString());
                    patient.setPatientWardnum("Ward A");
                    return patient;
                });

        var response = controller.admitPatient("PT-1", request, (Principal) () -> "admin@example.test");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("PT-1", response.getBody().getPatientId());
        assertEquals(LocalDate.now().toString(), response.getBody().getPatientAdmitdate());
        assertEquals("Ward A", response.getBody().getPatientWardnum());
        verify(nursingService).admitPatient("PT-1", "ward-1", "bed-1", "admin@example.test");
    }

    @Test
    void admissionCannotCreateASecondActiveStay() {
        Patient patient = new Patient();
        patient.setPatientId("PT-1");
        patient.setPatientAdmitdate(LocalDate.now().toString());
        PatientAdmissionRequest request = new PatientAdmissionRequest();
        request.setWardId("ward-1");
        request.setBedId("bed-1");
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Patient is already admitted"))
                .when(nursingService).admitPatient("PT-1", "ward-1", "bed-1", "admin@example.test");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.admitPatient(
                        "PT-1", request, (Principal) () -> "admin@example.test"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
