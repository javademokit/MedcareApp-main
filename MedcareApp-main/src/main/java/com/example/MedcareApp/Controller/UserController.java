package com.example.MedcareApp.Controller;


import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.services.UserService;
import com.example.MedcareApp.web.StaffAccountRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @PostMapping("/signup")
    public ResponseEntity<?> createUser(@RequestBody user newUser) {
        if (newUser.getEmailId() == null || newUser.getEmailId().isBlank()
                || newUser.getUserId() == null || newUser.getUserId().isBlank()
                || newUser.getPassword() == null || newUser.getPassword().length() < 12) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Enter a valid email and a password of at least 12 characters"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        try {
            userService.createUser(newUser);
            response.put("success", true);
            response.put("message", "Account created. A hospital administrator must assign staff access.");
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException exception) {
            response.put("success", false);
            response.put("message", exception.getMessage());
            return ResponseEntity.status(409).body(response);
        }
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody user userRequest,
            HttpServletRequest request,
            HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            userRequest.getEmailId(), userRequest.getPassword()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, httpResponse);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "Login successful");
            user account = userService.getUserByEmail(authentication.getName());
            if (account == null || !account.isActive()) {
                SecurityContextHolder.clearContext();
                return ResponseEntity.status(401)
                        .body(Map.of("success", false, "message", "Account is unavailable"));
            }
            responseBody.put("user", Map.of(
                    "username", account.getUserId(),
                    "emailId", account.getEmailId(),
                    "roles", account.getRoles()
            ));
            return ResponseEntity.ok(responseBody);
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Invalid email or password"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(Principal principal) {
        user currentUser = userService.getUserByEmail(principal.getName());
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Account is unavailable"));
        }
        return ResponseEntity.ok(Map.of(
                "username", currentUser.getUserId(),
                "emailId", currentUser.getEmailId(),
                "roles", currentUser.getRoles()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'CLINIC_ADMIN')")
    @GetMapping
    public ResponseEntity<List<user>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'CLINIC_ADMIN')")
    @PostMapping("/staff")
    public ResponseEntity<Map<String, Object>> createStaffAccount(
            @Valid @RequestBody StaffAccountRequest request,
            Authentication authentication) {
        Set<String> roles = Set.copyOf(request.getRoles());
        boolean superAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (!superAdmin && roles.stream().map(String::trim).map(String::toUpperCase)
                .anyMatch(Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")::contains)) {
            return ResponseEntity.status(403).body(Map.of(
                    "message", "Only a super administrator can create administrator accounts"));
        }

        user newUser = new user();
        newUser.setUserId(request.getUserId());
        newUser.setEmailId(request.getEmailId());
        newUser.setMobileNo(request.getMobileNo());
        newUser.setDoctorId(request.getDoctorId());
        newUser.setPassword(request.getPassword());
        try {
            user account = userService.createStaffUser(newUser, roles, request.getDoctorProfile());
            return ResponseEntity.status(201).body(Map.of(
                    "userId", account.getUserId(),
                    "emailId", account.getEmailId(),
                    "roles", account.getRoles(),
                    "active", account.isActive()));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                    "message", exception.getReason() == null ? "Staff account could not be created" : exception.getReason()));
        }
    }


    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'CLINIC_ADMIN')")
    @GetMapping("/{userId}")
    public ResponseEntity<List<user>> getUsersByUserId(@PathVariable String userId) {
        List<user> users = userService.findByUserId(userId);
        if (users.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(users);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'CLINIC_ADMIN')")
    @PutMapping("/{userId}")
    public ResponseEntity<user> updateUser(@PathVariable String userId, @RequestBody user updatedUser) {
        user result = userService.updateUser(userId, updatedUser);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'CLINIC_ADMIN')")
    @PutMapping("/{userId}/doctor-profile")
    public ResponseEntity<Map<String, Object>> assignDoctorProfile(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        user account = userService.assignDoctorProfile(userId, request.get("doctorId"));
        return ResponseEntity.ok(Map.of(
                "userId", account.getUserId(),
                "doctorId", account.getDoctorId() == null ? "" : account.getDoctorId()));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'CLINIC_ADMIN')")
    @PutMapping("/{userId}/roles")
    public ResponseEntity<Map<String, Object>> updateRoles(
            @PathVariable String userId,
            @RequestBody Map<String, List<String>> request,
            Authentication authentication) {
        List<String> requestedRoles = request.get("roles");
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "At least one role is required"));
        }
        boolean superAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
        Set<String> roleSet = Set.copyOf(requestedRoles);
        if (!superAdmin && roleSet.stream().map(String::trim).map(String::toUpperCase)
                .anyMatch(Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN")::contains)) {
            return ResponseEntity.status(403).body(Map.of(
                    "message", "Only a super administrator can assign administrator roles"));
        }
        if (authentication.getName().equalsIgnoreCase(userService.findByUserId(userId).stream()
                .findFirst().map(user::getEmailId).orElse(""))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "You cannot change your own roles"));
        }
        user account = userService.updateRoles(userId, roleSet);
        return ResponseEntity.ok(Map.of("userId", account.getUserId(), "roles", account.getRoles()));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'CLINIC_ADMIN')")
    @PatchMapping("/{userId}/active")
    public ResponseEntity<Map<String, Object>> updateActive(
            @PathVariable String userId,
            @RequestBody Map<String, Boolean> request) {
        Boolean active = request.get("active");
        if (active == null) return ResponseEntity.badRequest().body(Map.of("message", "Active status is required"));
        user account = userService.setActive(userId, active);
        return ResponseEntity.ok(Map.of("userId", account.getUserId(), "active", account.isActive()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

}
