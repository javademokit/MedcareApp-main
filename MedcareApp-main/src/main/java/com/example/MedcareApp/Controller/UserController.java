package com.example.MedcareApp.Controller;


import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
            response.put("message", "Sign up successful!");
            return ResponseEntity.ok(response);
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
            responseBody.put("user", Map.of("username", authentication.getName()));
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
                "emailId", currentUser.getEmailId()
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

    @GetMapping
    public ResponseEntity<List<user>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }


    @GetMapping("/{userId}")
    public ResponseEntity<List<user>> getUsersByUserId(@PathVariable String userId) {
        List<user> users = userService.findByUserId(userId);
        if (users.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<user> updateUser(@PathVariable String userId, @RequestBody user updatedUser) {
        user result = userService.updateUser(userId, updatedUser);
        if (result != null) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {

        userService.deleteUser(userId); // This returns a user or null
        return ResponseEntity.ok("user deleted");

    }

}




