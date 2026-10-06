package com.example.MedcareApp.config;

import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.UserService;
import com.example.MedcareApp.web.DoctorProfileRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(StaffAccountBootstrapProperties.class)
@RequiredArgsConstructor
public class StaffAccountBootstrapConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffAccountBootstrapConfiguration.class);
    private static final Map<String, String> ROLE_BY_ACCOUNT = Map.of(
            "pharmacist", "PHARMACIST",
            "doctor", "DOCTOR",
            "nurse", "NURSE",
            "technician", "LAB_TECHNICIAN");

    private final UserRepository userRepository;
    private final UserService userService;
    private final StaffAccountBootstrapProperties properties;

    @Bean
    ApplicationRunner bootstrapStaffAccounts() {
        return arguments -> {
            if (!properties.isEnabled()) {
                LOGGER.info("Configured staff-account bootstrap is disabled");
                return;
            }
            ROLE_BY_ACCOUNT.forEach(this::createAccountIfConfigured);
        };
    }

    private void createAccountIfConfigured(String accountKey, String role) {
        StaffAccountBootstrapProperties.StaffAccount settings =
                properties.getAccounts().get(accountKey);
        if (settings == null) {
            LOGGER.warn("Skipping configured {} bootstrap account: account properties are missing", accountKey);
            return;
        }
        if (!StringUtils.hasText(settings.getPassword())) {
            LOGGER.warn("Skipping configured {} bootstrap account: set its password environment variable", accountKey);
            return;
        }
        if (settings.getPassword().length() < 12) {
            throw new IllegalStateException(
                    "Configured " + accountKey + " bootstrap password must be at least 12 characters");
        }
        if (!StringUtils.hasText(settings.getUserId()) || !StringUtils.hasText(settings.getEmail())) {
            throw new IllegalStateException(
                    "Configured " + accountKey + " bootstrap account requires a user ID and email");
        }

        var existingAccounts = userRepository.findAllByEmailIdIgnoreCase(settings.getEmail().trim());
        if (existingAccounts.size() > 1) {
            throw new IllegalStateException(
                    "Multiple accounts use the configured " + accountKey + " bootstrap email");
        }
        if (!existingAccounts.isEmpty()) {
            user existing = existingAccounts.get(0);
            if (!existing.isActive() || !existing.getRoles().equals(Set.of(role))) {
                throw new IllegalStateException(
                        "Configured " + accountKey + " bootstrap email belongs to an inactive or differently privileged account");
            }
            LOGGER.info("Configured {} account already exists", accountKey);
            return;
        }

        user account = new user(
                settings.getPassword(), settings.getUserId(), settings.getEmail(), settings.getMobile());
        if ("DOCTOR".equals(role)) {
            userService.createStaffUser(account, Set.of(role), doctorProfile(settings));
        } else {
            userService.createStaffUser(account, Set.of(role));
        }
        LOGGER.info("Created configured {} account '{}'", accountKey, settings.getUserId());
    }

    private DoctorProfileRequest doctorProfile(StaffAccountBootstrapProperties.StaffAccount settings) {
        StaffAccountBootstrapProperties.DoctorProfile configured = settings.getDoctorProfile();
        if (configured == null) {
            throw new IllegalStateException("Doctor bootstrap account requires a doctor profile");
        }
        DoctorProfileRequest profile = new DoctorProfileRequest();
        profile.setDoctorName(StringUtils.hasText(configured.getName())
                ? configured.getName().trim() : settings.getUserId().trim());
        profile.setDoctorSpecialistName(StringUtils.hasText(configured.getSpecialty())
                ? configured.getSpecialty().trim() : "General medicine");
        profile.setDoctorMobileNo(settings.getMobile());
        profile.setDoctorDestination(configured.getDestination());
        profile.setDoctorAvailabletime(configured.getAvailableTimes() == null
                ? List.of()
                : configured.getAvailableTimes().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList());
        profile.setDoctorfee(configured.getConsultationFee());
        return profile;
    }
}
