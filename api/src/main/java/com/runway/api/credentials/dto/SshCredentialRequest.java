package com.runway.api.credentials.dto;

import jakarta.validation.constraints.NotBlank;

public record SshCredentialRequest(@NotBlank String name, @NotBlank String privateKey, String passphrase) {
}
