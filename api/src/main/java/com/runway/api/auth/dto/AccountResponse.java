package com.runway.api.auth.dto;

import com.runway.api.auth.User;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String email, String name, Instant createdAt) {

    public static AccountResponse from(User user) {
        return new AccountResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }
}
