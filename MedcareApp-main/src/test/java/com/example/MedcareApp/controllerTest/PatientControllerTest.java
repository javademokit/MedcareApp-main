package com.example.MedcareApp.controllerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Controller.PatientController;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.web.PatientAdmissionRequest;
import java.time.LocalDate;
import java.util.List;
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
    @InjectMocks private PatientController controller;

    @Test
    void admissionUpdatesTheExistingPatientInsteadOfCreatingAnotherRecord() {
        Patient patient = new Patient();
        patient.setPatientId("PT-1");
        patient.setPatientName("A Patient");
        when(patientRepository.findAllByPatientId("PT-1")).thenReturn(List.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PatientAdmissionRequest request = new PatientAdmissionRequest();
        request.setWardNumber("Ward A");

        var response = controller.admitPatient("PT-1", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("PT-1", response.getBody().getPatientId());
        assertEquals(LocalDate.now().toString(), response.getBody().getPatientAdmitdate());
        assertEquals("Ward A", response.getBody().getPatientWardnum());
    }

    @Test
    void admissionCannotCreateASecondActiveStay() {
        Patient patient = new Patient();
        patient.setPatientId("PT-1");
        patient.setPatientAdmitdate(LocalDate.now().toString());
        when(patientRepository.findAllByPatientId("PT-1")).thenReturn(List.of(patient));
        PatientAdmissionRequest request = new PatientAdmissionRequest();
        request.setWardNumber("Ward A");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.admitPatient("PT-1", request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
