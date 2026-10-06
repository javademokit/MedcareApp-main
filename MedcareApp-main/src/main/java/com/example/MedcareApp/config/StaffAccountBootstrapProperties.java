package com.example.MedcareApp.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.security.bootstrap-staff")
public class StaffAccountBootstrapProperties {
    private boolean enabled = true;
    private Map<String, StaffAccount> accounts = new LinkedHashMap<>();

    @Data
    public static class StaffAccount {
        private String userId;
        private String email;
        private String mobile;
        private String password;
        private DoctorProfile doctorProfile = new DoctorProfile();
    }

    @Data
    public static class DoctorProfile {
        private String name = "Doctor Account";
        private String specialty = "General medicine";
        private String destination = "";
        private List<String> availableTimes = new ArrayList<>();
        private double consultationFee;
    }
}
