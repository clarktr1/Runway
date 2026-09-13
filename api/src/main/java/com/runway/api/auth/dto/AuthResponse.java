package com.runway.api.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String email,
        String name,
        UUID organizationId,
        String organizationName,
        String role) {
}
