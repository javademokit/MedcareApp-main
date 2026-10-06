package com.example.MedcareApp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.UserService;
import com.example.MedcareApp.web.DoctorProfileRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;

class StaffAccountBootstrapConfigurationTest {
    @Test
    void createsOnlyConfiguredStaffRolesAndLinksDoctorToNewProfile() throws Exception {
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        when(userRepository.findAllByEmailIdIgnoreCase(any())).thenReturn(List.of());

        StaffAccountBootstrapProperties properties = configuredProperties();
        ApplicationRunner runner = new StaffAccountBootstrapConfiguration(
                userRepository, userService, properties).bootstrapStaffAccounts();

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(userService).createStaffUser(any(user.class), eq(Set.of("PHARMACIST")));
        verify(userService).createStaffUser(any(user.class), eq(Set.of("NURSE")));
        verify(userService).createStaffUser(any(user.class), eq(Set.of("LAB_TECHNICIAN")));
        org.mockito.ArgumentCaptor<DoctorProfileRequest> doctorProfile =
                org.mockito.ArgumentCaptor.forClass(DoctorProfileRequest.class);
        verify(userService).createStaffUser(any(user.class), eq(Set.of("DOCTOR")), doctorProfile.capture());
        assertEquals("Configured Doctor", doctorProfile.getValue().getDoctorName());
        assertEquals("Cardiology", doctorProfile.getValue().getDoctorSpecialistName());
        assertEquals(List.of("09:00"), doctorProfile.getValue().getDoctorAvailabletime());
    }

    @Test
    void leavesAnUnconfiguredStaffAccountUncreated() throws Exception {
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        when(userRepository.findAllByEmailIdIgnoreCase(any())).thenReturn(List.of());
        StaffAccountBootstrapProperties properties = configuredProperties();
        properties.getAccounts().get("nurse").setPassword("");

        ApplicationRunner runner = new StaffAccountBootstrapConfiguration(
                userRepository, userService, properties).bootstrapStaffAccounts();

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(userService, never()).createStaffUser(any(user.class), eq(Set.of("NURSE")));
        verify(userService).createStaffUser(any(user.class), eq(Set.of("PHARMACIST")));
        verify(userService).createStaffUser(any(user.class), eq(Set.of("LAB_TECHNICIAN")));
        verify(userService).createStaffUser(any(user.class), eq(Set.of("DOCTOR")), any());
    }

    @Test
    void refusesToTakeOverAnExistingAccountWithDifferentRoles() throws Exception {
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        user existingAccount = new user("encoded", "pharmacy", "pharmacy@medcare.local", "");
        existingAccount.setRoles(Set.of("CRM_EXECUTIVE"));
        when(userRepository.findAllByEmailIdIgnoreCase("pharmacy@medcare.local"))
                .thenReturn(List.of(existingAccount));

        ApplicationRunner runner = new StaffAccountBootstrapConfiguration(
                userRepository, userService, configuredProperties()).bootstrapStaffAccounts();

        assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));
        verify(userService, never()).createStaffUser(any(user.class), eq(Set.of("PHARMACIST")));
    }

    @Test
    void leavesExistingBootstrapAccountsUnchanged() throws Exception {
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        user existingPharmacist = new user("encoded", "pharmacy", "pharmacy@medcare.local", "");
        existingPharmacist.setRoles(Set.of("PHARMACIST"));
        when(userRepository.findAllByEmailIdIgnoreCase("pharmacy@medcare.local"))
                .thenReturn(List.of(existingPharmacist));
        when(userRepository.findAllByEmailIdIgnoreCase("doctor@medcare.local")).thenReturn(List.of());
        when(userRepository.findAllByEmailIdIgnoreCase("nurse@medcare.local")).thenReturn(List.of());
        when(userRepository.findAllByEmailIdIgnoreCase("technician@medcare.local")).thenReturn(List.of());

        ApplicationRunner runner = new StaffAccountBootstrapConfiguration(
                userRepository, userService, configuredProperties()).bootstrapStaffAccounts();

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(userService, never()).createStaffUser(any(user.class), eq(Set.of("PHARMACIST")));
    }

    private StaffAccountBootstrapProperties configuredProperties() {
        StaffAccountBootstrapProperties properties = new StaffAccountBootstrapProperties();
        properties.setEnabled(true);
        properties.setAccounts(Map.of(
                "pharmacist", account("pharmacy", "pharmacy@medcare.local", "pharmacy-password-123"),
                "doctor", doctorAccount(),
                "nurse", account("nurse", "nurse@medcare.local", "nurse-password-123"),
                "technician", account("technician", "technician@medcare.local", "technician-password-123")));
        return properties;
    }

    private StaffAccountBootstrapProperties.StaffAccount doctorAccount() {
        StaffAccountBootstrapProperties.StaffAccount account =
                account("doctor", "doctor@medcare.local", "doctor-password-123");
        StaffAccountBootstrapProperties.DoctorProfile profile =
                new StaffAccountBootstrapProperties.DoctorProfile();
        profile.setName("Configured Doctor");
        profile.setSpecialty("Cardiology");
        profile.setAvailableTimes(List.of("09:00"));
        profile.setConsultationFee(500);
        account.setDoctorProfile(profile);
        return account;
    }

    private StaffAccountBootstrapProperties.StaffAccount account(
            String userId, String email, String password) {
        StaffAccountBootstrapProperties.StaffAccount account =
                new StaffAccountBootstrapProperties.StaffAccount();
        account.setUserId(userId);
        account.setEmail(email);
        account.setPassword(password);
        return account;
    }
}
