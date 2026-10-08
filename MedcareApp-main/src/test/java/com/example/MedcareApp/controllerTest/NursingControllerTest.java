package com.example.MedcareApp.controllerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Controller.NursingController;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.NursingService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Test
    void nurseListExcludesAccountsWithoutNurseEmploymentRecords() {
        user admin = new user();
        admin.setId("admin-1");
        admin.setRoles(Set.of("HOSPITAL_ADMIN"));
        user employedNurse = new user();
        employedNurse.setId("nurse-1");
        employedNurse.setEmailId("nurse@example.test");
        employedNurse.setUserId("nurse.login");
        employedNurse.setRoles(Set.of("NURSE"));
        user unrelatedAccount = new user();
        unrelatedAccount.setId("other-1");
        unrelatedAccount.setEmailId("doctor@example.test");
        unrelatedAccount.setUserId("doctor.login");
        unrelatedAccount.setRoles(Set.of("NURSE"));
        Employee nurseEmployment = new Employee();
        nurseEmployment.setEmployeeCode("NUR-12345678");
        nurseEmployment.setEmployeeType("NURSE");
        nurseEmployment.setStatus("ACTIVE");
        nurseEmployment.setEmail("nurse@example.test");

        when(userRepository.findAllByEmailIdIgnoreCase("admin@example.test")).thenReturn(List.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(employedNurse, unrelatedAccount));
        when(nursingService.getNurseProfiles()).thenReturn(List.of());
        when(nursingService.getNurseEmployments()).thenReturn(List.of(nurseEmployment));

        List<java.util.Map<String, Object>> nurses = controller.getNurses(() -> "admin@example.test");

        assertEquals(1, nurses.size());
        assertEquals("NUR-12345678", nurses.get(0).get("employeeCode"));
        assertEquals("NUR-12345678", nurses.get(0).get("id"));
        assertEquals("nurse-1", nurses.get(0).get("accountId"));
    }

    @Test
    void nurseCanLookUpAssignmentsForSelectedNurseAccount() {
        user signedInNurse = new user();
        signedInNurse.setId("signed-in-nurse");
        signedInNurse.setRoles(Set.of("NURSE"));
        List<java.util.Map<String, Object>> assignments = List.of(java.util.Map.of("patientId", "PT-100"));
        when(userRepository.findAllByEmailIdIgnoreCase("nurse@example.test")).thenReturn(List.of(signedInNurse));
        when(nursingService.getNurseDashboard("selected-nurse")).thenReturn(assignments);

        List<java.util.Map<String, Object>> result = controller.nurseAssignments(
                "selected-nurse", () -> "nurse@example.test");

        assertEquals(assignments, result);
        verify(nursingService).getNurseDashboard("selected-nurse");
    }

    @Test
    void nonNurseCannotUseNurseAssignmentLookup() {
        user admin = new user();
        admin.setId("admin-1");
        admin.setRoles(Set.of("HOSPITAL_ADMIN"));
        when(userRepository.findAllByEmailIdIgnoreCase("admin@example.test")).thenReturn(List.of(admin));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                controller.nurseAssignments("nurse-1", () -> "admin@example.test"));

        assertEquals(403, error.getStatusCode().value());
    }
}
