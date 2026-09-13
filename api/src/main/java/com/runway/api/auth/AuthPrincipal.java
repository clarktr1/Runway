package com.runway.api.auth;

import java.util.UUID;

public record AuthPrincipal(UUID userId, UUID organizationId, String email) {
}
