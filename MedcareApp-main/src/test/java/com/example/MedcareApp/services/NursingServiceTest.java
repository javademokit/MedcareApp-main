package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.nursing.NurseProfile;
import com.example.MedcareApp.Entity.nursing.NurseShiftRoster;
import com.example.MedcareApp.Entity.nursing.PatientAssignment;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.nursing.WardBed;
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
import com.example.MedcareApp.Interafce.nursing.WardRoomRepository;
import com.example.MedcareApp.Interafce.nursing.BedStatusHistoryRepository;
import com.example.MedcareApp.Interafce.nursing.BedStaySegmentRepository;
import com.example.MedcareApp.Entity.nursing.WardRoom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NursingServiceTest {
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;
    @Mock private NurseProfileRepository nurseProfileRepository;
    @Mock private WardRepository wardRepository;
    @Mock private WardBedRepository wardBedRepository;
    @Mock private WardRoomRepository roomRepository;
    @Mock private BedStatusHistoryRepository bedHistoryRepository;
    @Mock private BedStaySegmentRepository bedStayRepository;
    @Mock private MongoTemplate mongoTemplate;
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
        nurseAccount.setEmailId("nurse@example.test");
        nurseAccount.setRoles(Set.of("NURSE"));
        Employee employment = new Employee();
        employment.setEmployeeType("NURSE");
        employment.setEmployeeCode("EMP-10001");
        employment.setStatus("ACTIVE");

        when(userRepository.findById("crm@example.test")).thenReturn(Optional.empty());
        when(userRepository.findAllByEmailIdIgnoreCase("crm@example.test")).thenReturn(List.of(manager));
        when(patientRepository.findAllByPatientId("PT-100")).thenReturn(List.of(patient));
        when(wardRepository.findById("ward-1")).thenReturn(Optional.of(ward));
        when(nurseProfileRepository.findByAccountId("nurse-1")).thenReturn(Optional.of(profile));
        when(userRepository.findById("nurse-1")).thenReturn(Optional.of(nurseAccount));
        when(mongoTemplate.findOne(any(), eq(Employee.class))).thenReturn(employment);
        when(rosterRepository.findByWardIdAndStatus("ward-1", "SCHEDULED")).thenReturn(List.of(roster));
        when(assignmentRepository.findAll()).thenReturn(List.of(existing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.assignPatient("PT-100", "nurse-1", "PRIMARY", "MORNING", "crm@example.test"));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("nurse-to-patient limit"));
    }

    @Test
    void createsMissingNurseProfileFromActiveAccountAndHrEmploymentDuringAssignment() {
        String today = LocalDate.now().toString();
        Patient patient = new Patient();
        patient.setPatientId("PT-200");
        patient.setPatientAdmitdate(today);
        patient.setPatientWardId("ward-1");
        patient.setPatientBedId("bed-1");

        Ward ward = new Ward();
        ward.setId("ward-1");
        ward.setName("ICU");
        ward.setMaxPatientsPerNurse(2);

        user manager = new user();
        manager.setId("crm-1");
        manager.setEmailId("crm@example.test");
        manager.setRoles(Set.of("CRM_EXECUTIVE"));
        user nurseAccount = new user();
        nurseAccount.setId("nurse-1");
        nurseAccount.setEmailId("nurse@example.test");
        nurseAccount.setRoles(Set.of("NURSE"));
        Employee employment = new Employee();
        employment.setEmployeeType("NURSE");
        employment.setEmployeeCode("NR-10001");
        employment.setFirstName("Anita");
        employment.setLastName("Sharma");
        employment.setEmail("nurse@example.test");
        employment.setMobile("555-0100");
        employment.setStatus("ACTIVE");
        employment.setProfessionalInfo(java.util.Map.of(
                "qualification", "BSc Nursing",
                "registrationNumber", "RN-123",
                "specialization", "ICU"));

        NurseShiftRoster roster = new NurseShiftRoster();
        roster.setNurseId("NR-10001");
        roster.setWardId("ward-1");
        roster.setShift("MORNING");
        roster.setStartDate(today);
        roster.setEndDate(today);
        roster.setStatus("SCHEDULED");

        when(userRepository.findById("crm@example.test")).thenReturn(Optional.empty());
        when(userRepository.findAllByEmailIdIgnoreCase("crm@example.test")).thenReturn(List.of(manager));
        when(userRepository.findById("NR-10001")).thenReturn(Optional.empty());
        when(userRepository.findById("nurse-1")).thenReturn(Optional.of(nurseAccount));
        when(userRepository.findAll()).thenReturn(List.of(nurseAccount));
        when(nurseProfileRepository.findByAccountId("NR-10001")).thenReturn(Optional.empty());
        when(nurseProfileRepository.findByEmployeeId("NR-10001")).thenReturn(Optional.empty());
        when(nurseProfileRepository.save(any(NurseProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.findOne(any(Query.class), eq(Employee.class))).thenReturn(employment);
        when(patientRepository.findAllByPatientId("PT-200")).thenReturn(List.of(patient));
        when(wardRepository.findById("ward-1")).thenReturn(Optional.of(ward));
        when(rosterRepository.findByWardIdAndStatus("ward-1", "SCHEDULED")).thenReturn(List.of(roster));
        when(assignmentRepository.findAll()).thenReturn(List.of());
        when(assignmentRepository.findByPatientIdAndStatus("PT-200", "ACTIVE")).thenReturn(List.of());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.save(any(PatientAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PatientAssignment assignment = service.assignPatient(
                "PT-200", "NR-10001", "PRIMARY", "MORNING", "crm@example.test");

        ArgumentCaptor<NurseProfile> profileCaptor = ArgumentCaptor.forClass(NurseProfile.class);
        verify(nurseProfileRepository).save(profileCaptor.capture());
        assertEquals("nurse-1", profileCaptor.getValue().getAccountId());
        assertEquals("NR-10001", profileCaptor.getValue().getEmployeeId());
        assertEquals("Anita Sharma", profileCaptor.getValue().getName());
        assertEquals("RN-123", profileCaptor.getValue().getLicenseNumber());
        assertEquals("NR-10001", assignment.getNurseId());
        assertEquals("NR-10001", patient.getPatientNurseId());
    }

    @Test
    void nurseProfileUsesTheActiveHrEmploymentIdAndDetails() {
        user admin = new user();
        admin.setId("admin-1");
        admin.setRoles(Set.of("HOSPITAL_ADMIN"));
        user nurseAccount = new user();
        nurseAccount.setId("nurse-1");
        nurseAccount.setEmailId("nurse.account@example.test");
        nurseAccount.setMobileNo("(555) 0199");
        nurseAccount.setUserId("nurse.login");
        nurseAccount.setRoles(Set.of("NURSE"));
        Employee employment = new Employee();
        employment.setEmployeeType("NURSE");
        employment.setEmployeeCode("EMP-10001");
        employment.setFirstName("Anita");
        employment.setLastName("Sharma");
        employment.setEmail("nurse.hr@example.test");
        employment.setMobile("555-0199");
        employment.setStatus("ACTIVE");
        employment.setProfessionalInfo(java.util.Map.of(
                "registrationNumber", "RN-123",
                "qualification", "BSc Nursing",
                "specialization", "ICU"));

        when(userRepository.findById("admin@example.test")).thenReturn(Optional.empty());
        when(userRepository.findAllByEmailIdIgnoreCase("admin@example.test")).thenReturn(List.of(admin));
        when(userRepository.findById("nurse-1")).thenReturn(Optional.of(nurseAccount));
        when(mongoTemplate.findOne(any(Query.class), eq(Employee.class))).thenReturn(null);
        when(mongoTemplate.find(any(Query.class), eq(Employee.class))).thenReturn(List.of(employment));
        when(nurseProfileRepository.findByAccountId("nurse-1")).thenReturn(Optional.empty());
        when(nurseProfileRepository.findAll()).thenReturn(List.of());
        when(nurseProfileRepository.save(any(NurseProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(user.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NurseProfile saved = service.saveNurseProfile("nurse-1", new NurseProfile(), "admin@example.test");

        assertEquals("EMP-10001", saved.getEmployeeId());
        assertEquals("Anita Sharma", saved.getName());
        assertEquals("555-0199", saved.getPhone());
        assertEquals("RN-123", saved.getLicenseNumber());
        assertEquals("BSc Nursing", saved.getQualification());
        assertEquals("ICU", saved.getSpecialization());
        assertEquals("EMP-10001", nurseAccount.getEmployeeCode());
        verify(userRepository).save(nurseAccount);
    }

    @Test
    void createsWalkInNurseAsOneEmploymentRecordAndLinksProfileToItsEmployeeId() {
        when(mongoTemplate.find(any(Query.class), eq(Employee.class))).thenReturn(List.of());
        when(mongoTemplate.findOne(any(Query.class), eq(Employee.class))).thenReturn(null);
        when(mongoTemplate.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nurseProfileRepository.findAll()).thenReturn(List.of());
        when(nurseProfileRepository.save(any(NurseProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee created = service.createWalkInNurse(
                "Riya", "Shah", "+1 (555) 010-2020", null, "BSc Nursing", "RN-100", "ICU");

        assertEquals("NURSE", created.getEmployeeType());
        assertEquals("ACTIVE", created.getStatus());
        assertTrue(created.getEmployeeCode().matches("NR-WK-[A-F0-9]{8}"));
        ArgumentCaptor<NurseProfile> profileCaptor = ArgumentCaptor.forClass(NurseProfile.class);
        verify(nurseProfileRepository).save(profileCaptor.capture());
        assertEquals(created.getEmployeeCode(), profileCaptor.getValue().getEmployeeId());
        assertEquals("Riya Shah", profileCaptor.getValue().getName());
    }

    @Test
    void rejectsWalkInNurseWhenMobileAlreadyBelongsToAnExistingNurse() {
        Employee existing = new Employee();
        existing.setEmployeeType("NURSE");
        existing.setMobile("15550102020");
        when(mongoTemplate.find(any(Query.class), eq(Employee.class))).thenReturn(List.of(existing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.createWalkInNurse(
                        "Riya", "Shah", "+1 (555) 010-2020", null, null, null, null));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void schedulesAndAssignsWalkInNurseUsingTheSameEmployeeId() {
        String nurseId = "NR-WK-A1B2C3D4";
        String today = LocalDate.now().toString();
        NurseProfile profile = new NurseProfile();
        profile.setEmployeeId(nurseId);
        profile.setName("Riya Shah");
        profile.setStatus("ACTIVE");
        Employee employment = new Employee();
        employment.setEmployeeType("NURSE");
        employment.setEmployeeCode(nurseId);
        employment.setStatus("ACTIVE");
        user admin = new user();
        admin.setId("admin-1");
        admin.setRoles(Set.of("HOSPITAL_ADMIN"));
        Ward ward = new Ward();
        ward.setId("ward-1");
        ward.setName("General");
        ward.setMaxPatientsPerNurse(8);
        Patient patient = new Patient();
        patient.setPatientId("PT-100");
        patient.setPatientAdmitdate(today);
        patient.setPatientWardId("ward-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(nurseProfileRepository.findByAccountId(nurseId)).thenReturn(Optional.empty());
        when(nurseProfileRepository.findByEmployeeId(nurseId)).thenReturn(Optional.of(profile));
        when(wardRepository.findById("ward-1")).thenReturn(Optional.of(ward));
        when(mongoTemplate.findOne(any(Query.class), eq(Employee.class))).thenReturn(employment);
        when(rosterRepository.findAll()).thenReturn(List.of());
        when(rosterRepository.save(any(NurseShiftRoster.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NurseShiftRoster roster = new NurseShiftRoster();
        roster.setNurseId(nurseId);
        roster.setWardId("ward-1");
        roster.setShift("MORNING");
        roster.setStartDate(today);
        roster.setEndDate(today);
        NurseShiftRoster savedRoster = service.saveRoster(roster, "admin-1");
        when(rosterRepository.findByWardIdAndStatus("ward-1", "SCHEDULED")).thenReturn(List.of(savedRoster));
        when(patientRepository.findAllByPatientId("PT-100")).thenReturn(List.of(patient));
        when(assignmentRepository.findAll()).thenReturn(List.of());
        when(assignmentRepository.findByPatientIdAndStatus("PT-100", "ACTIVE")).thenReturn(List.of());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.save(any(PatientAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientAssignment assignment = service.assignPatient(
                "PT-100", nurseId, "PRIMARY", "MORNING", "admin-1");

        assertEquals(nurseId, savedRoster.getNurseId());
        assertEquals(nurseId, assignment.getNurseId());
        assertEquals(nurseId, patient.getPatientNurseId());
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
        when(rosterRepository.findAll()).thenReturn(List.of(roster));
        when(assignmentRepository.findAll()).thenReturn(List.of(wardAssignment, otherWardAssignment));

        List<PatientAssignment> result = service.getAssignments("head-1");

        assertEquals(1, result.size());
        assertEquals("ward-1", result.get(0).getWardId());
    }

    @Test
    void rejectsBedStatusChangeForOccupiedBed() {
        user admin = new user();
        admin.setId("admin-1");
        admin.setRoles(Set.of("HOSPITAL_ADMIN"));
        WardBed occupiedBed = new WardBed();
        occupiedBed.setId("bed-1");
        occupiedBed.setWardId("ward-1");
        occupiedBed.setStatus("OCCUPIED");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(wardBedRepository.findById("bed-1")).thenReturn(Optional.of(occupiedBed));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.updateBedStatus("ward-1", "bed-1", "VACANT", "manual", null, "admin-1"));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("patient transfer or discharge"));
    }

    @Test
    void rejectsRoomWithInvalidBedCapacityBeforePersistence() {
        WardRoom room = new WardRoom();
        room.setWardId("ward-1");
        room.setRoomNumber("101");
        room.setAcType("AC");
        room.setCategory("PRIVATE");
        room.setBedCapacity(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.saveRoom(room, "admin-1"));

        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("bed capacity"));
    }

    @Test
    void createsBedsAndStatusHistoryForRoomCapacity() {
        user admin = new user();
        admin.setId("admin-1");
        admin.setRoles(Set.of("HOSPITAL_ADMIN"));
        Ward ward = new Ward();
        ward.setId("ward-1");
        ward.setName("General");
        WardRoom room = new WardRoom();
        room.setWardId("ward-1");
        room.setRoomNumber("201");
        room.setAcType("AC");
        room.setCategory("PRIVATE");
        room.setBedCapacity(2);

        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(wardRepository.findById("ward-1")).thenReturn(Optional.of(ward));
        when(roomRepository.save(any(WardRoom.class))).thenAnswer(invocation -> {
            WardRoom saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID().toString());
            return saved;
        });
        when(wardBedRepository.save(any(WardBed.class))).thenAnswer(invocation -> {
            WardBed saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID().toString());
            return saved;
        });

        WardRoom saved = service.saveRoom(room, "admin-1");

        assertEquals(2, saved.getBedCapacity());
        ArgumentCaptor<WardBed> beds = ArgumentCaptor.forClass(WardBed.class);
        verify(wardBedRepository, times(2)).save(beds.capture());
        assertEquals(List.of("201-A", "201-B"), beds.getAllValues().stream().map(WardBed::getBedNumber).toList());
        assertTrue(beds.getAllValues().stream().allMatch(bed -> saved.getId().equals(bed.getRoomId())));
        verify(bedHistoryRepository, times(2)).save(any());
    }
}
