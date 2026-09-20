package com.runway.api.remotehosts;

import com.runway.api.auth.OrganizationRepository;
import com.runway.api.common.BadRequestException;
import com.runway.api.common.NotFoundException;
import com.runway.api.credentials.CredentialEncryptionService;
import com.runway.api.credentials.SshCredential;
import com.runway.api.credentials.SshCredentialRepository;
import com.runway.api.jobs.JobRepository;
import com.runway.api.remotehosts.dto.RemoteHostRequest;
import com.runway.api.remotehosts.dto.RemoteHostResponse;
import com.runway.api.remotehosts.dto.TestConnectionResponse;
import com.runway.api.ssh.PinnedHostKeyVerifier;
import com.runway.api.ssh.SshKeyParser;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoteHostService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final RemoteHostRepository remoteHostRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final JobRepository jobRepository;
    private final OrganizationRepository organizationRepository;
    private final CredentialEncryptionService encryptionService;

    public RemoteHostService(
            RemoteHostRepository remoteHostRepository,
            SshCredentialRepository sshCredentialRepository,
            JobRepository jobRepository,
            OrganizationRepository organizationRepository,
            CredentialEncryptionService encryptionService) {
        this.remoteHostRepository = remoteHostRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.jobRepository = jobRepository;
        this.organizationRepository = organizationRepository;
        this.encryptionService = encryptionService;
    }

    public List<RemoteHostResponse> list(UUID organizationId) {
        return remoteHostRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(RemoteHostResponse::from)
                .toList();
    }

    public RemoteHostResponse get(UUID id, UUID organizationId) {
        return RemoteHostResponse.from(findOwnedHost(id, organizationId));
    }

    @Transactional
    public RemoteHostResponse create(UUID organizationId, RemoteHostRequest request) {
        SshCredential credential = findOwnedCredential(request.sshCredentialId(), organizationId);
        RemoteHost host = new RemoteHost(
                organizationRepository.getReferenceById(organizationId),
                request.name(),
                request.hostname(),
                request.port(),
                request.username(),
                credential);
        return RemoteHostResponse.from(remoteHostRepository.save(host));
    }

    @Transactional
    public RemoteHostResponse update(UUID id, UUID organizationId, RemoteHostRequest request) {
        RemoteHost host = findOwnedHost(id, organizationId);
        SshCredential credential = findOwnedCredential(request.sshCredentialId(), organizationId);
        host.update(request.name(), request.hostname(), request.port(), request.username(), credential);
        return RemoteHostResponse.from(host);
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) {
        RemoteHost host = findOwnedHost(id, organizationId);
        if (jobRepository.existsSshCommandJobReferencingRemoteHost(organizationId, id.toString())) {
            throw new BadRequestException(
                    "This remote host is still used by one or more jobs and cannot be deleted");
        }
        remoteHostRepository.delete(host);
    }

    @Transactional
    public TestConnectionResponse testConnection(UUID id, UUID organizationId) {
        RemoteHost host = findOwnedHost(id, organizationId);
        AttemptResult result = attemptConnection(host, host.getPinnedHostKeyFingerprint());

        if (!result.success()) {
            return new TestConnectionResponse(
                    false, result.presentedFingerprint(), result.presentedAlgorithm(), false, result.errorMessage());
        }

        boolean newlyPinned = host.getPinnedHostKeyFingerprint() == null;
        if (newlyPinned) {
            host.pin(result.presentedFingerprint(), result.presentedAlgorithm());
        }
        return new TestConnectionResponse(
                true, result.presentedFingerprint(), result.presentedAlgorithm(), newlyPinned, null);
    }

    @Transactional
    public TestConnectionResponse repin(UUID id, UUID organizationId) {
        RemoteHost host = findOwnedHost(id, organizationId);
        AttemptResult result = attemptConnection(host, null);

        if (!result.success()) {
            return new TestConnectionResponse(
                    false, result.presentedFingerprint(), result.presentedAlgorithm(), false, result.errorMessage());
        }

        host.pin(result.presentedFingerprint(), result.presentedAlgorithm());
        return new TestConnectionResponse(true, result.presentedFingerprint(), result.presentedAlgorithm(), true, null);
    }

    private AttemptResult attemptConnection(RemoteHost host, String expectedFingerprint) {
        SshCredential credential = host.getSshCredential();
        String privateKey = encryptionService.decrypt(credential.getEncryptedPrivateKey());
        String passphrase = credential.getEncryptedPassphrase() == null
                ? null
                : encryptionService.decrypt(credential.getEncryptedPassphrase());

        PinnedHostKeyVerifier verifier = new PinnedHostKeyVerifier(expectedFingerprint);
        try (SSHClient client = new SSHClient()) {
            client.setConnectTimeout(CONNECT_TIMEOUT_MS);
            client.setTimeout(CONNECT_TIMEOUT_MS);
            client.addHostKeyVerifier(verifier);
            client.connect(host.getHostname(), host.getPort());

            KeyProvider keyProvider = SshKeyParser.parse(privateKey, passphrase).keyProvider();
            client.authPublickey(host.getUsername(), keyProvider);

            return new AttemptResult(true, verifier.getPresentedFingerprint(), verifier.getPresentedAlgorithm(), null);
        } catch (IOException e) {
            return new AttemptResult(
                    false, verifier.getPresentedFingerprint(), verifier.getPresentedAlgorithm(), e.getMessage());
        }
    }

    private RemoteHost findOwnedHost(UUID id, UUID organizationId) {
        return remoteHostRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Remote host not found"));
    }

    private SshCredential findOwnedCredential(UUID id, UUID organizationId) {
        return sshCredentialRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new BadRequestException("sshCredentialId does not reference a known credential"));
    }

    private record AttemptResult(
            boolean success, String presentedFingerprint, String presentedAlgorithm, String errorMessage) {
    }
}
