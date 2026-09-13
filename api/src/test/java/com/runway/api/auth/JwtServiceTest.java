package com.runway.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("unit-test-secret-key-must-be-at-least-32-bytes-long", 60);

    @Test
    void issuesATokenThatParsesBackToTheSamePrincipal() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "dev@runway.dev");

        String token = jwtService.issue(principal);
        AuthPrincipal parsed = jwtService.parse(token);

        assertThat(parsed).isEqualTo(principal);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "dev@runway.dev");
        String token = jwtService.issue(principal);

        JwtService otherService =
                new JwtService("a-completely-different-secret-key-of-32-plus-bytes", 60);

        assertThatThrownBy(() -> otherService.parse(token)).isInstanceOf(RuntimeException.class);
    }
}
