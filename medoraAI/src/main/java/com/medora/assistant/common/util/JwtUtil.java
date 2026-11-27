package com.medora.assistant.common.util;

import com.medora.assistant.config.MedoraProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(MedoraProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = props.getJwt().getAccessMinutes() * 60_000L;
        this.refreshExpirationMs = props.getJwt().getRefreshDays() * 24 * 60 * 60_000L;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        return generateToken(userId, email, role, accessExpirationMs);
    }

    public String generateRefreshToken(UUID userId) {
        return generateToken(userId, "", "", refreshExpirationMs);
    }

    private String generateToken(UUID userId, String email, String role, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
            .subject(userId.toString())
            .claims(Map.of("email", email, "role", role))
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }
}
