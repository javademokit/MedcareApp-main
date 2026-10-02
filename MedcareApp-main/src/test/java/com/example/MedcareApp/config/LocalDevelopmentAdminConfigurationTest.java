package com.example.MedcareApp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LocalDevelopmentAdminConfigurationTest {
    @Mock private UserRepository userRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void givesAllRolesToConfiguredLocalAccountWhenOtherAccountsExist() throws Exception {
        user account = new user("encoded", "admin", "admin@medcare.local", "0000000000");
        when(userRepository.findAllByEmailIdIgnoreCase("admin@medcare.local")).thenReturn(List.of(account));
        when(userRepository.findByUserId("admin")).thenReturn(List.of(account));
        when(userRepository.save(account)).thenReturn(account);
        UserService userService = new UserService(userRepository, patientRepository, doctorRepository, passwordEncoder);
        ApplicationRunner runner = new LocalDevelopmentAdminConfiguration().localDevelopmentAdmin(
                userRepository, userService, "admin", "admin@medcare.local", "0000000000", "admin12345678");

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository).save(account);
        assertEquals(11, account.getRoles().size());
        assertEquals("SUPER_ADMIN", account.getRoles().stream()
                .filter("SUPER_ADMIN"::equals)
                .findFirst()
                .orElseThrow());
    }
}
