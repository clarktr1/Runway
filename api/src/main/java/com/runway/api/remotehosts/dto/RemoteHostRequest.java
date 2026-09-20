package com.runway.api.remotehosts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record RemoteHostRequest(
        @NotBlank String name,
        @NotBlank String hostname,
        @Positive int port,
        @NotBlank String username,
        @NotNull UUID sshCredentialId) {
}
