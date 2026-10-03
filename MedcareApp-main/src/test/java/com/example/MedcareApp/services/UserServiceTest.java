package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.web.DoctorProfileRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    @Test
    void promotesOnlyTheUniqueAccountMatchingTheConfiguredEmail() {
        user account = new user();
        account.setUserId("demo-admin");
        account.setEmailId("demo@medcare.local");
        account.setRoles(java.util.Set.of("PATIENT"));
        when(userRepository.findAllByEmailIdIgnoreCase("demo@medcare.local")).thenReturn(List.of(account));
        when(userRepository.save(account)).thenReturn(account);

        user promoted = userService.promoteExistingAccountToSuperAdmin(" DEMO@MEDCARE.LOCAL ");

        assertEquals(java.util.Set.of("PATIENT", "SUPER_ADMIN"), promoted.getRoles());
        verify(userRepository).save(account);
    }

    @Test
    void refusesPromotionWhenTheConfiguredEmailIsNotUnique() {
        when(userRepository.findAllByEmailIdIgnoreCase("demo@medcare.local"))
                .thenReturn(List.of(new user(), new user()));

        assertThrows(IllegalStateException.class,
                () -> userService.promoteExistingAccountToSuperAdmin("demo@medcare.local"));
    }

    @Test
    void createsStaffAccountsWithEncodedPasswordsAndAssignedRoles() {
        user request = new user("StrongStaffPass2026", "crm-one", "CRM@example.test", "5551234567");
        when(userRepository.findAllByEmailIdIgnoreCase("crm@example.test")).thenReturn(List.of());
        when(userRepository.findByUserId("crm-one")).thenReturn(List.of());
        when(passwordEncoder.encode("StrongStaffPass2026")).thenReturn("encoded-password");
        when(userRepository.save(request)).thenReturn(request);

        user created = userService.createStaffUser(request, Set.of(" crm_executive "));

        assertEquals("crm@example.test", created.getEmailId());
        assertEquals("encoded-password", created.getPassword());
        assertEquals(Set.of("CRM_EXECUTIVE"), created.getRoles());
        verify(userRepository).save(request);
    }

    @Test
    void refusesPatientRoleWhenCreatingAStaffAccount() {
        user request = new user("StrongStaffPass2026", "crm-one", "crm@example.test", "5551234567");

        assertThrows(ResponseStatusException.class,
                () -> userService.createStaffUser(request, Set.of("PATIENT")));
    }

    @Test
    void createsPatientLoginWithEncodedPasswordAndPatientProfile() {
        when(userRepository.findAllByEmailIdIgnoreCase("patient@example.test")).thenReturn(List.of());
        when(userRepository.findByUserId("patient-one")).thenReturn(List.of());
        when(passwordEncoder.encode("StrongPatientPass2026")).thenReturn("encoded-password");
        when(userRepository.save(any(user.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.findAllByPatientEmailIdIgnoreCase("patient@example.test")).thenReturn(List.of());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        user created = userService.createPatientAccount(
                " patient-one ", "Patient@Example.Test", "5557654321", "StrongPatientPass2026",
                "Pat Example", "42", "Female", "12 Example Street");

        assertEquals("patient-one", created.getUserId());
        assertEquals("patient@example.test", created.getEmailId());
        assertEquals("encoded-password", created.getPassword());
        assertEquals(Set.of("PATIENT"), created.getRoles());
        verify(patientRepository, org.mockito.Mockito.times(2)).save(any(Patient.class));
    }

    @Test
    void createsDoctorAccountOnlyWhenLinkedToAnExistingDoctorProfile() {
        user request = new user("StrongDoctorPass2026", "doctor-one", "doctor@example.test", "5551234567");
        request.setDoctorId("doctor-profile-1");
        when(doctorRepository.findById("doctor-profile-1")).thenReturn(Optional.of(new Doctor()));
        when(userRepository.findAllByEmailIdIgnoreCase("doctor@example.test")).thenReturn(List.of());
        when(userRepository.findByUserId("doctor-one")).thenReturn(List.of());
        when(passwordEncoder.encode("StrongDoctorPass2026")).thenReturn("encoded-password");
        when(userRepository.save(request)).thenReturn(request);

        user created = userService.createStaffUser(request, Set.of("DOCTOR"));

        assertEquals("doctor-profile-1", created.getDoctorId());
        assertEquals(Set.of("DOCTOR"), created.getRoles());
    }

    @Test
    void createsDoctorProfileAlongsideDoctorAccount() {
        user request = new user("StrongDoctorPass2026", "doctor-two", "doctor2@example.test", "5551234567");
        DoctorProfileRequest profile = new DoctorProfileRequest();
        profile.setDoctorName("Dr. New");
        profile.setDoctorSpecialistName("Cardiology");
        profile.setDoctorAvailabletime(List.of("09:00", "09:30"));
        profile.setDoctorfee(500);
        when(userRepository.findAllByEmailIdIgnoreCase("doctor2@example.test")).thenReturn(List.of());
        when(userRepository.findByUserId("doctor-two")).thenReturn(List.of());
        when(passwordEncoder.encode("StrongDoctorPass2026")).thenReturn("encoded-password");
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor doctor = invocation.getArgument(0);
            doctor.setId("doctor-profile-new");
            return doctor;
        });
        when(userRepository.save(request)).thenReturn(request);

        user created = userService.createStaffUser(request, Set.of("DOCTOR"), profile);

        assertEquals("doctor-profile-new", created.getDoctorId());
        verify(doctorRepository).save(any(Doctor.class));
        verify(userRepository).save(request);
    }
}
