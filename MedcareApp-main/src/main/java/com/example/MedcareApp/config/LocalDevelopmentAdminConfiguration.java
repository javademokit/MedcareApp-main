package com.example.MedcareApp.config;

import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.UserService;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Configuration
@Profile("local")
public class LocalDevelopmentAdminConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDevelopmentAdminConfiguration.class);
    private static final Set<String> ALL_ROLES = Set.of(
            "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR", "NURSE",
            "RECEPTIONIST", "CRM_EXECUTIVE", "BILLING_EXECUTIVE", "PHARMACIST",
            "LAB_TECHNICIAN", "PATIENT");

    @Bean
    ApplicationRunner localDevelopmentAdmin(
            UserRepository userRepository,
            UserService userService,
            @Value("${app.local-admin.user-id}") String userId,
            @Value("${app.local-admin.email}") String email,
            @Value("${app.local-admin.mobile}") String mobile,
            @Value("${app.local-admin.password}") String password) {
        return arguments -> {
            if (!StringUtils.hasText(userId) || !StringUtils.hasText(email)
                    || !StringUtils.hasText(password) || password.length() < 12) {
                throw new IllegalStateException("Local admin requires a user ID, email, and password of at least 12 characters");
            }

            List<user> emailMatches = userRepository.findAllByEmailIdIgnoreCase(email.trim());
            if (emailMatches.size() > 1) {
                throw new IllegalStateException("Multiple accounts use the configured local admin email");
            }
            if (emailMatches.size() == 1) {
                user account = emailMatches.get(0);
                userService.updateRoles(account.getUserId(), ALL_ROLES);
                LOGGER.info("Granted all roles to the configured local development account: {}", account.getUserId());
                return;
            }

            if (!userRepository.findByUserId(userId.trim()).isEmpty()) {
                throw new IllegalStateException("The configured local admin user ID is already in use");
            }
            user initialAdmin = new user(password, userId.trim(), email.trim(), mobile);
            user createdAdmin = userService.createUser(initialAdmin);
            userService.updateRoles(createdAdmin.getUserId(), ALL_ROLES);
            LOGGER.warn("Created local-only all-roles admin account '{}'. Do not use the local profile in production.",
                    createdAdmin.getUserId());
        };
    }
}
