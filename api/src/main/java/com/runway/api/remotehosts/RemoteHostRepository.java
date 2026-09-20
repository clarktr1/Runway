package com.runway.api.remotehosts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemoteHostRepository extends JpaRepository<RemoteHost, UUID> {
    List<RemoteHost> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<RemoteHost> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsBySshCredentialIdAndOrganizationId(UUID sshCredentialId, UUID organizationId);
}
