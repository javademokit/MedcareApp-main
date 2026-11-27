package com.medora.assistant.common.dto;

import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserDto user
) {
    public record UserDto(UUID id, String name, String role) {}
}
