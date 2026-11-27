package com.pharmacy.service;

import com.pharmacy.common.ApiException;
import com.pharmacy.config.JwtService;
import com.pharmacy.model.User;
import com.pharmacy.repo.Repos.UserRepository;
import com.pharmacy.web.Dto.*;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public LoginResponse login(LoginRequest r) {
        User u = users.findByUsername(r.username()).orElse(null);
        if (u == null || !u.isActive() || !encoder.matches(r.password(), u.getPasswordHash())) {
            audit.log("LOGIN_FAILED", "User", r.username(), "Invalid credentials");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password");
        }
        var roles = new ArrayList<>(u.getRoles());
        audit.log("LOGIN", "User", u.getId(), u.getUsername());
        return new LoginResponse(jwt.create(u.getUsername(), roles), u.getUsername(), u.getFullName(), roles);
    }
}
