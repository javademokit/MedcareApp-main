package com.example.MedcareApp.config;

import com.example.MedcareApp.services.UserService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
    @Value("${app.cors.allowed-origin:http://localhost:3000}")
    private String allowedOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserService userService) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                var account = userService.authenticate(
                        (String) authentication.getPrincipal(),
                        (String) authentication.getCredentials());
                if (account == null) {
                    throw new BadCredentialsException("Invalid email or password");
                }

                if (!account.isActive()) throw new BadCredentialsException("Account is unavailable");
                var authorities = account.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                var principal = User.withUsername(account.getEmailId())
                        .password(account.getPassword())
                        .authorities(authorities)
                        .build();
                return UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            SecurityContextRepository securityContextRepository,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/billing/appointment-invoices/gateway/payu/callback",
                                "/api/ambulance/tracking/pair", "/api/ambulance/tracking/location"))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/signup", "/api/users/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/users/logout").authenticated()
                        .requestMatchers("/api/doctor-portal/**").hasRole("DOCTOR")
                        .requestMatchers("/api/patient-portal/**").hasRole("PATIENT")
                        .requestMatchers("/api/hr/**", "/api/reports/hr/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE")
                        .requestMatchers("/api/employees/**", "/api/employee-types/**", "/api/departments/**",
                                "/api/designations/**", "/api/shifts/**", "/api/attendance/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR")
                        .requestMatchers(HttpMethod.GET, "/api/leaves/mine").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE",
                                "DOCTOR", "NURSE", "HEAD_NURSE", "RECEPTIONIST", "CRM_EXECUTIVE",
                                "BILLING_EXECUTIVE", "PHARMACIST", "LAB_TECHNICIAN")
                        .requestMatchers(HttpMethod.POST, "/api/leaves").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE",
                                "DOCTOR", "NURSE", "HEAD_NURSE", "RECEPTIONIST", "CRM_EXECUTIVE",
                                "BILLING_EXECUTIVE", "PHARMACIST", "LAB_TECHNICIAN")
                        .requestMatchers("/api/leaves/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR")
                        .requestMatchers("/api/salary/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE")
                        .requestMatchers("/api/payroll/me/**", "/api/payslips/*/download")
                                .hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE",
                                "DOCTOR", "NURSE", "HEAD_NURSE", "RECEPTIONIST", "CRM_EXECUTIVE",
                                "BILLING_EXECUTIVE", "PHARMACIST", "LAB_TECHNICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/payslips/*/*").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE",
                                "DOCTOR", "NURSE", "HEAD_NURSE", "RECEPTIONIST", "CRM_EXECUTIVE",
                                "BILLING_EXECUTIVE", "PHARMACIST", "LAB_TECHNICIAN")
                        .requestMatchers("/api/payroll/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE", "CRM_EXECUTIVE")
                        .requestMatchers("/api/payslips/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE")
                        .requestMatchers("/api/payroll/run/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE")
                        .requestMatchers(HttpMethod.POST,
                                "/api/billing/appointment-invoices/gateway/payu/callback").permitAll()
                        .requestMatchers("/api/billing/appointment-invoices/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "RECEPTIONIST",
                                "CRM_EXECUTIVE", "BILLING_EXECUTIVE", "FINANCE")
                        .requestMatchers("/api/dashboard/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR",
                                "RECEPTIONIST", "CRM_EXECUTIVE", "BILLING_EXECUTIVE",
                                "PHARMACIST", "LAB_TECHNICIAN", "FINANCE", "HR")
                        .requestMatchers("/api/users/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/doctors").authenticated()
                        .requestMatchers("/api/doctors/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/patients/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN",
                                "RECEPTIONIST", "CRM_EXECUTIVE", "BILLING_EXECUTIVE")
                        .requestMatchers("/api/patients/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HEAD_NURSE",
                                "CRM_EXECUTIVE", "BILLING_EXECUTIVE")
                        .requestMatchers(HttpMethod.POST, "/api/appointments1").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "RECEPTIONIST",
                                "CRM_EXECUTIVE")
                        .requestMatchers(HttpMethod.GET, "/api/appointments1/availability").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "RECEPTIONIST",
                                "CRM_EXECUTIVE")
                        .requestMatchers("/api/appointments1/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "RECEPTIONIST",
                                "CRM_EXECUTIVE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/nurses/*/assignments").hasAnyRole(
                                "NURSE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/nurses").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                "NURSE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/nurses/walk-in").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/dashboard", "/api/nursing/handovers").hasAnyRole(
                                "NURSE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/rosters/mine").hasAnyRole("NURSE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/shift-swaps").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                "NURSE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/shift-swaps").hasAnyRole("NURSE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/shift-swaps/*/decision").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/patients/*/care-records/**",
                                "/api/nursing/patients/*/handovers", "/api/nursing/handovers/*/acknowledge")
                                .hasAnyRole("NURSE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/rooms", "/api/nursing/beds/available",
                                "/api/nursing/beds/summary", "/api/nursing/bed-stays").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "RECEPTIONIST", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/bed-waiting-list").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "RECEPTIONIST", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/bed-waiting-list",
                                "/api/nursing/bed-waiting-list/*/cancel").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "RECEPTIONIST", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/beds/*/history").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/rooms", "/api/nursing/rooms/bulk")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/nursing/rooms/**")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/beds/*/cleaning-complete")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/wards", "/api/nursing/wards/**",
                                "/api/nursing/rooms/**", "/api/nursing/beds/**",
                                "/api/nursing/rosters", "/api/nursing/assignments",
                                "/api/nursing/unassigned-patients",
                                "/api/nursing/patients/assignments").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/nursing/care-records").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/wards/*/assignments",
                                "/api/nursing/patients/*/assignments", "/api/nursing/patients/*/auto-assign")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                        "HEAD_NURSE")
                        .requestMatchers(HttpMethod.PUT, "/api/nursing/patients/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                "HEAD_NURSE")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/wards")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/nursing/wards/*/beds", "/api/nursing/rosters")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.PUT, "/api/nursing/wards/*/beds/*")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.PUT, "/api/nursing/wards/**")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/nursing/nurses/**")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "HEAD_NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/wards").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                "HEAD_NURSE")
                        .requestMatchers("/api/medical-tests/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR", "CRM_EXECUTIVE", "LAB_TECHNICIAN")
                        .requestMatchers("/api/urinetests/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR", "CRM_EXECUTIVE", "LAB_TECHNICIAN")
                        .requestMatchers("/api/emergency/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST", "CRM_EXECUTIVE")
                        .requestMatchers(HttpMethod.POST, "/api/ambulance/tracking/pair",
                                "/api/ambulance/tracking/location").permitAll()
                        .requestMatchers("/api/ambulance/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                "RECEPTIONIST", "BILLING_EXECUTIVE", "FINANCE")
                        .requestMatchers("/api/pharmacy/invoices/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE",
                                "PHARMACIST", "BILLING_EXECUTIVE", "FINANCE")
                        .requestMatchers("/api/pharmacy/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "PHARMACIST")
                        .requestMatchers("/api/discharges/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR", "CRM_EXECUTIVE", "BILLING_EXECUTIVE")
                        .requestMatchers(HttpMethod.GET, "/api/staff/shifts/mine").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/staff/shifts/mine/*/check-in",
                                "/api/staff/shifts/mine/*/check-out").hasRole("DOCTOR")
                        .requestMatchers("/api/staff/shifts/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE")
                        .requestMatchers("/api/daily-updates/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "DOCTOR", "NURSE")
                        .requestMatchers("/api/reports/**").hasAnyRole(
                                "SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "CRM_EXECUTIVE", "BILLING_EXECUTIVE")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
