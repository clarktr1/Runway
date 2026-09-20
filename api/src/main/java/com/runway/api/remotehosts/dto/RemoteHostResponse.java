package com.runway.api.remotehosts.dto;

import com.runway.api.remotehosts.RemoteHost;
import java.time.Instant;
import java.util.UUID;

public record RemoteHostResponse(
        UUID id,
        String name,
        String hostname,
        int port,
        String username,
        UUID sshCredentialId,
        String sshCredentialName,
        String pinnedHostKeyFingerprint,
        String pinnedHostKeyAlgorithm,
        Instant pinnedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static RemoteHostResponse from(RemoteHost host) {
        return new RemoteHostResponse(
                host.getId(),
                host.getName(),
                host.getHostname(),
                host.getPort(),
                host.getUsername(),
                host.getSshCredential().getId(),
                host.getSshCredential().getName(),
                host.getPinnedHostKeyFingerprint(),
                host.getPinnedHostKeyAlgorithm(),
                host.getPinnedAt(),
                host.getCreatedAt(),
                host.getUpdatedAt());
    }
}
