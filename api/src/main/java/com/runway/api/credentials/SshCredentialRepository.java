package com.runway.api.credentials;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SshCredentialRepository extends JpaRepository<SshCredential, UUID> {
    List<SshCredential> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<SshCredential> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
