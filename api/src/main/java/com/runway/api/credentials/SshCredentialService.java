package com.runway.api.credentials;

import com.runway.api.auth.OrganizationRepository;
import com.runway.api.common.BadRequestException;
import com.runway.api.common.NotFoundException;
import com.runway.api.credentials.dto.SshCredentialRequest;
import com.runway.api.credentials.dto.SshCredentialResponse;
import com.runway.api.remotehosts.RemoteHostRepository;
import com.runway.api.ssh.SshKeyParser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SshCredentialService {

    private final SshCredentialRepository sshCredentialRepository;
    private final RemoteHostRepository remoteHostRepository;
    private final OrganizationRepository organizationRepository;
    private final CredentialEncryptionService encryptionService;

    public SshCredentialService(
            SshCredentialRepository sshCredentialRepository,
            RemoteHostRepository remoteHostRepository,
            OrganizationRepository organizationRepository,
            CredentialEncryptionService encryptionService) {
        this.sshCredentialRepository = sshCredentialRepository;
        this.remoteHostRepository = remoteHostRepository;
        this.organizationRepository = organizationRepository;
        this.encryptionService = encryptionService;
    }

    public List<SshCredentialResponse> list(UUID organizationId) {
        return sshCredentialRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(SshCredentialResponse::from)
                .toList();
    }

    @Transactional
    public SshCredentialResponse create(UUID organizationId, UUID userId, SshCredentialRequest request) {
        SshKeyParser.ParsedKey parsedKey = SshKeyParser.parse(request.privateKey(), request.passphrase());

        SshCredential credential = new SshCredential(
                organizationRepository.getReferenceById(organizationId),
                userId,
                request.name(),
                encryptionService.encrypt(request.privateKey()),
                request.passphrase() == null || request.passphrase().isBlank()
                        ? null
                        : encryptionService.encrypt(request.passphrase()),
                parsedKey.fingerprint(),
                parsedKey.publicKeyPreview());

        return SshCredentialResponse.from(sshCredentialRepository.save(credential));
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) {
        SshCredential credential = findOwnedCredential(id, organizationId);
        if (remoteHostRepository.existsBySshCredentialIdAndOrganizationId(id, organizationId)) {
            throw new BadRequestException(
                    "This SSH credential is still used by one or more remote hosts and cannot be deleted");
        }
        sshCredentialRepository.delete(credential);
    }

    private SshCredential findOwnedCredential(UUID id, UUID organizationId) {
        return sshCredentialRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("SSH credential not found"));
    }
}
