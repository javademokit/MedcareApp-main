package com.medora.assistant.api;

import com.medora.assistant.common.dto.LoginRequest;
import com.medora.assistant.common.dto.LoginResponse;
import com.medora.assistant.common.util.JwtUtil;
import com.medora.assistant.config.MedoraProperties;
import com.medora.assistant.document.UserEntity;
import com.medora.assistant.document.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MedoraProperties medoraProperties;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil, MedoraProperties medoraProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.medoraProperties = medoraProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        LoginResponse response = new LoginResponse(
            accessToken,
            refreshToken,
            medoraProperties.getJwt().getAccessMinutes() * 60,
            new LoginResponse.UserDto(user.getId(), user.getName(), user.getRole())
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !jwtUtil.isValid(refreshToken)) {
            return ResponseEntity.status(401).build();
        }
        UUID userId = jwtUtil.getUserId(refreshToken);
        UserEntity user = userRepository.findById(userId).orElseThrow();
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@RequestHeader("Authorization") String auth) {
        String token = auth.substring(7);
        var claims = jwtUtil.parseToken(token);
        return ResponseEntity.ok(Map.of(
            "id", claims.getSubject(),
            "email", claims.get("email", String.class),
            "role", claims.get("role", String.class)
        ));
    }
}
