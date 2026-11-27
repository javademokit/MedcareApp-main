package com.medora.assistant.config;

import com.medora.assistant.document.UserEntity;
import com.medora.assistant.document.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

@Configuration
public class AdminBootstrap {

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                        MedoraProperties props) {
        return args -> {
            if (!userRepository.existsByEmail(props.getAdmin().getEmail())) {
                UserEntity admin = new UserEntity(
                    UUID.randomUUID(),
                    props.getAdmin().getEmail(),
                    "Admin",
                    passwordEncoder.encode(props.getAdmin().getPassword()),
                    "ADMIN"
                );
                userRepository.save(admin);
                System.out.println("Admin user created: " + props.getAdmin().getEmail());
            }
        };
    }
}
