package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.nursing.NurseProfile;
import com.example.MedcareApp.Entity.nursing.NurseShiftRoster;
import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.ConsultationRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.Interafce.nursing.NurseHandoverRepository;
import com.example.MedcareApp.Interafce.nursing.NurseProfileRepository;
import com.example.MedcareApp.Interafce.nursing.NurseShiftRosterRepository;
import com.example.MedcareApp.Interafce.nursing.NurseShiftSwapRepository;
import com.example.MedcareApp.Interafce.nursing.NursingCareRecordRepository;
import com.example.MedcareApp.Interafce.nursing.PatientAssignmentRepository;
import com.example.MedcareApp.Interafce.nursing.WardBedRepository;
import com.example.MedcareApp.Interafce.nursing.WardRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NursingServiceTest {
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;
    @Mock private NurseProfileRepository nurseProfileRepository;
    @Mock private WardRepository wardRepository;
    @Mock private WardBedRepository wardBedRepository;
    @Mock private NurseShiftRosterRepository rosterRepository;
    @Mock private NurseShiftSwapRepository shiftSwapRepository;
    @Mock private PatientAssignmentRepository assignmentRepository;
    @Mock private NurseHandoverRepository handoverRepository;
    @Mock private NursingCareRecordRepository careRecordRepository;
    @Mock private ConsultationRepository consultationRepository;
    @InjectMocks private NursingService service;

    @Test
    void rejectsPrimaryAssignmentWhenNursePatientRatioIsFull() {
        String today = LocalDate.now().toString();
        Patient patient = new Patient();
        patient.setPatientId("PT-100");
        patient.setPatientAdmitdate(today);
        patient.setPatientWardId("ward-1");

        Ward ward = new Ward();
        ward.setId("ward-1");
        ward.setName("ICU");
        ward.setMaxPatientsPerNurse(1);

        NurseProfile profile = new NurseProfile();
        profile.setAccountId("nurse-1");
        profile.setName("Nurse One");
        profile.setStatus("ACTIVE");

        NurseShiftRoster roster = new NurseShiftRoster();
        roster.setNurseId("nurse-1");
        roster.setWardId("ward-1");
        roster.setShift("MORNING");
        roster.setStartDate(today);
        roster.setEndDate(today);

        PatientAssignment existing = new PatientAssignment();
        existing.setPatientId("PT-OLD");
        existing.setWardId("ward-1");
        existing.setNurseId("nurse-1");
        existing.setRole("PRIMARY");
        existing.setStatus("ACTIVE");

        user manager = new user();
        manager.setId("crm-1");
        manager.setEmailId("crm@example.test");
        manager.setRoles(Set.of("CRM_EXECUTIVE"));
        user nurseAccount = new user();
        nurseAccount.setId("nurse-1");
        nurseAccount.setRoles(Set.of("NURSE"));

        when(userRepository.findById("crm@example.test")).thenReturn(Optional.empty());
        when(userRepository.findAllByEmailIdIgnoreCase("crm@example.test")).thenReturn(List.of(manager));
        when(patientRepository.findAllByPatientId("PT-100")).thenReturn(List.of(patient));
        when(wardRepository.findById("ward-1")).thenReturn(Optional.of(ward));
        when(nurseProfileRepository.findByAccountId("nurse-1")).thenReturn(Optional.of(profile));
        when(userRepository.findById("nurse-1")).thenReturn(Optional.of(nurseAccount));
        when(rosterRepository.findByWardIdAndStatus("ward-1", "SCHEDULED")).thenReturn(List.of(roster));
        when(assignmentRepository.findByNurseIdAndStatus("nurse-1", "ACTIVE")).thenReturn(List.of(existing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.assignPatient("PT-100", "nurse-1", "PRIMARY", "MORNING", "crm@example.test"));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("nurse-to-patient limit"));
    }

    @Test
    void headNurseCanReadOnlyAssignmentsInRosteredWard() {
        String today = LocalDate.now().toString();
        user headNurse = new user();
        headNurse.setId("head-1");
        headNurse.setRoles(Set.of("HEAD_NURSE"));

        NurseShiftRoster roster = new NurseShiftRoster();
        roster.setNurseId("head-1");
        roster.setWardId("ward-1");
        roster.setStatus("SCHEDULED");
        roster.setEndDate(today);

        PatientAssignment wardAssignment = new PatientAssignment();
        wardAssignment.setWardId("ward-1");
        PatientAssignment otherWardAssignment = new PatientAssignment();
        otherWardAssignment.setWardId("ward-2");

        when(userRepository.findById("head-1")).thenReturn(Optional.of(headNurse));
        when(rosterRepository.findByNurseIdAndStatus("head-1", "SCHEDULED")).thenReturn(List.of(roster));
        when(assignmentRepository.findAll()).thenReturn(List.of(wardAssignment, otherWardAssignment));

        List<PatientAssignment> result = service.getAssignments("head-1");

        assertEquals(1, result.size());
        assertEquals("ward-1", result.get(0).getWardId());
    }
}
