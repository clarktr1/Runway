package com.runway.api.credentials.dto;

import com.runway.api.credentials.SshCredential;
import java.time.Instant;
import java.util.UUID;

public record SshCredentialResponse(
        UUID id, String name, String keyFingerprint, String publicKeyPreview, Instant createdAt, Instant updatedAt) {

    public static SshCredentialResponse from(SshCredential credential) {
        return new SshCredentialResponse(
                credential.getId(),
                credential.getName(),
                credential.getKeyFingerprint(),
                credential.getPublicKeyPreview(),
                credential.getCreatedAt(),
                credential.getUpdatedAt());
    }
}
