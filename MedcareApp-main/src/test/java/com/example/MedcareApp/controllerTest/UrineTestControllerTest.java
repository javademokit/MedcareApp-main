package com.example.MedcareApp.controllerTest;

import com.example.MedcareApp.Controller.UrineTestController;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.test.UrineTest;
import com.example.MedcareApp.Interafce.UrineTestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(UrineTestController.class)
class UrineTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrineTestRepository urineTestService;

    @Test
    @WithMockUser
    void testGetAllUrineTests() throws Exception {
        Patient patient = new Patient();
        patient.setPatientId("P001");
        patient.setPatientName("Ravi");
        patient.setPatientEmailId("ravi@example.com");
        patient.setPatientmobileNo("9876543210");
        patient.setPatientAddress("Delhi");
        patient.setPatientPrescription("Take rest");
        patient.setPatientAppointmentdate("2025-06-01");
        patient.setPatientAdmitdate("2025-06-02");
        patient.setPatientWardnum("A12");
        patient.setPatientDischargedate("2025-06-04");
        patient.setPatientNurseassign("Nurse Annie");

        UrineTest test = new UrineTest(
                "UT001",
                patient,
                "Negative",
                "Normal",
                "Trace",
                "6.0",
                "None",
                "Healthy patient"
        );

        List<UrineTest> mockList = List.of(test);

        Mockito.when(urineTestService.findAll()).thenReturn(mockList);

        mockMvc.perform(get("/api/urinetests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("UT001"))
                .andExpect(jsonPath("$[0].patient.patientName").value("Ravi"))
                .andExpect(jsonPath("$[0].glucose").value("Negative"));
    }
}
