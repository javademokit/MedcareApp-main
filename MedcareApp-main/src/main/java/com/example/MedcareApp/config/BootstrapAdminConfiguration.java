package com.example.MedcareApp.config;

import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.UserRepository;
import com.example.MedcareApp.services.UserService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Configuration
@Profile("!local")
public class BootstrapAdminConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "app.security.bootstrap-admin.enabled", havingValue = "true")
    ApplicationRunner bootstrapAdmin(
            UserRepository userRepository,
            UserService userService,
            @Value("${app.security.bootstrap-admin.user-id:}") String userId,
            @Value("${app.security.bootstrap-admin.email:}") String email,
            @Value("${app.security.bootstrap-admin.mobile:}") String mobile,
            @Value("${app.security.bootstrap-admin.password:}") String password,
            @Value("${app.security.bootstrap-admin.promote-existing-email:}") String promoteExistingEmail) {
        return arguments -> {
            if (userRepository.count() != 0) {
                if (StringUtils.hasText(promoteExistingEmail)) {
                    user account = userService.promoteExistingAccountToSuperAdmin(promoteExistingEmail);
                    LOGGER.info("Promoted the explicitly configured existing account to super-admin: {}",
                            account.getUserId());
                } else {
                    LOGGER.info("Skipping bootstrap admin because user accounts already exist");
                }
                return;
            }
            if (!StringUtils.hasText(userId) || !StringUtils.hasText(email)
                    || !StringUtils.hasText(password) || password.length() < 12) {
                throw new IllegalStateException(
                        "Bootstrap admin requires user-id, email, and a password of at least 12 characters");
            }
            user initialAdmin = new user(password, userId, email, mobile);
            userService.createUser(initialAdmin);
            userService.updateRoles(userId, Set.of("SUPER_ADMIN"));
            LOGGER.info("Created the configured initial super-admin account");
        };
    }
}
