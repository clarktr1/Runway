package com.runway.api.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.runway.api.auth.Organization;
import com.runway.api.auth.OrganizationRepository;
import com.runway.api.auth.User;
import com.runway.api.auth.UserRepository;
import com.runway.api.credentials.CredentialEncryptionService;
import com.runway.api.credentials.SshCredential;
import com.runway.api.credentials.SshCredentialRepository;
import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.LogStream;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import com.runway.api.remotehosts.RemoteHost;
import com.runway.api.remotehosts.RemoteHostRepository;
import com.runway.api.ssh.PinnedHostKeyVerifier;
import com.runway.api.ssh.SshKeyParser;
import com.runway.api.testsupport.EmbeddedSshServer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.schmizz.sshj.SSHClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SshJobExecutorTest {

    private static final String USERNAME = "testuser";

    private static final String PRIVATE_KEY =
            """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            QyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBgAAAJhstTV+bLU1
            fgAAAAtzc2gtZWQyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBg
            AAAED+G5biVjxsRt2rGCaDzNCtrNXgCw3RvgTwAeJuXTIeEWFxCoRdesFItoXpCZJVse1T
            uC79q5v3+KTumK8DAKIGAAAAD3J1bndheS10ZXN0LWtleQECAwQFBg==
            -----END OPENSSH PRIVATE KEY-----
            """;

    private static final String PUBLIC_KEY_LINE =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGFxCoRdesFItoXpCZJVse1TuC79q5v3+KTumK8DAKIG runway-test-key";

    @Autowired
    private SshJobExecutor sshJobExecutor;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SshCredentialRepository sshCredentialRepository;

    @Autowired
    private RemoteHostRepository remoteHostRepository;

    @Autowired
    private CredentialEncryptionService encryptionService;

    private Organization organization;
    private EmbeddedSshServer sshServer;

    @AfterEach
    void cleanUp() throws IOException {
        if (sshServer != null) {
            sshServer.close();
        }
        if (organization != null) {
            organizationRepository.deleteById(organization.getId());
        }
    }

    private RemoteHost remoteHost(boolean pinned) throws Exception {
        sshServer = new EmbeddedSshServer(USERNAME, PUBLIC_KEY_LINE);
        organization = organizationRepository.save(new Organization("SSH Executor Test Org"));
        User user = userRepository.save(
                new User("ssh-exec-" + System.nanoTime() + "@runway.dev", "unused-hash", "SSH Test User"));

        SshKeyParser.ParsedKey parsedKey = SshKeyParser.parse(PRIVATE_KEY, null);
        SshCredential credential = sshCredentialRepository.save(new SshCredential(
                organization,
                user.getId(),
                "Test Key",
                encryptionService.encrypt(PRIVATE_KEY),
                null,
                parsedKey.fingerprint(),
                parsedKey.publicKeyPreview()));

        RemoteHost host = new RemoteHost(
                organization, "Test Host", sshServer.getHostname(), sshServer.getPort(), USERNAME, credential);
        if (pinned) {
            PinnedHostKeyVerifier verifier = capturePresentedHostKey(host);
            host.pin(verifier.getPresentedFingerprint(), verifier.getPresentedAlgorithm());
        }
        return remoteHostRepository.save(host);
    }

    /**
     * Connects once in capture mode (expected fingerprint {@code null}) to learn the fingerprint
     * sshj will present for this host — the same mechanism {@code RemoteHostService} uses to
     * establish a first-time pin.
     */
    private PinnedHostKeyVerifier capturePresentedHostKey(RemoteHost host) throws IOException {
        PinnedHostKeyVerifier verifier = new PinnedHostKeyVerifier(null);
        try (SSHClient client = new SSHClient()) {
            client.setConnectTimeout(5_000);
            client.addHostKeyVerifier(verifier);
            client.connect(host.getHostname(), host.getPort());
        }
        return verifier;
    }

    private Job job(RemoteHost host, String command) {
        return job(host, command, null);
    }

    private Job job(RemoteHost host, String command, Integer timeoutSeconds) {
        return new Job(
                organization,
                "SSH Job",
                null,
                JobType.SSH_COMMAND,
                Map.of("remoteHostId", host.getId().toString(), "command", command),
                true,
                timeoutSeconds,
                1,
                Map.of(),
                null,
                null);
    }

    @Test
    void succeedsAndCapturesOrderedStdoutAndStderr() throws Exception {
        RemoteHost host = remoteHost(true);
        Job job = job(host, "/bin/sh -c \"echo out-line; echo err-line 1>&2\"");
        List<LogLine> logs = new CopyOnWriteArrayList<>();

        JobExecutor.Outcome outcome = sshJobExecutor.execute(job, logs::add, new CancellationToken());

        assertThat(outcome.result()).isEqualTo(JobExecutor.Result.SUCCESS);
        assertThat(outcome.exitCode()).isEqualTo(0);
        assertThat(logs)
                .anyMatch(l -> l.stream() == LogStream.STDOUT && l.message().equals("out-line"));
        assertThat(logs)
                .anyMatch(l -> l.stream() == LogStream.STDERR && l.message().equals("err-line"));
    }

    @Test
    void reportsANonZeroExitCodeAsFailed() throws Exception {
        RemoteHost host = remoteHost(true);
        Job job = job(host, "/bin/sh -c \"exit 7\"");

        JobExecutor.Outcome outcome = sshJobExecutor.execute(job, log -> {}, new CancellationToken());

        assertThat(outcome.result()).isEqualTo(JobExecutor.Result.FAILED);
        assertThat(outcome.exitCode()).isEqualTo(7);
    }

    @Test
    void timesOutWhenTheCommandRunsTooLong() throws Exception {
        RemoteHost host = remoteHost(true);
        Job job = job(host, "/bin/sh -c \"sleep 5\"", 1);

        JobExecutor.Outcome outcome = sshJobExecutor.execute(job, log -> {}, new CancellationToken());

        assertThat(outcome.result()).isEqualTo(JobExecutor.Result.TIMEOUT);
    }

    @Test
    void cancellationStopsTheLocalChannelPromptly() throws Exception {
        RemoteHost host = remoteHost(true);
        Job job = job(host, "/bin/sh -c \"sleep 30\"");
        CancellationToken token = new CancellationToken();
        AtomicReference<JobExecutor.Outcome> outcome = new AtomicReference<>();

        Thread runner = new Thread(() -> outcome.set(sshJobExecutor.execute(job, log -> {}, token)));
        runner.start();
        Thread.sleep(500);
        token.cancel();
        runner.join(10_000);

        assertThat(outcome.get().result()).isEqualTo(JobExecutor.Result.CANCELLED);
    }

    @Test
    void failsClosedWhenTheHostHasNeverBeenVerified() throws Exception {
        RemoteHost host = remoteHost(false);
        Job job = job(host, "/bin/sh -c \"echo should-not-run\"");

        JobExecutor.Outcome outcome = sshJobExecutor.execute(job, log -> {}, new CancellationToken());

        assertThat(outcome.result()).isEqualTo(JobExecutor.Result.FAILED);
        assertThat(outcome.errorMessage()).containsIgnoringCase("test connection");
    }

    @Test
    void failsClosedWhenThePresentedHostKeyDoesNotMatchThePinnedFingerprint() throws Exception {
        RemoteHost host = remoteHost(true);
        host.pin("SHA256:0000000000000000000000000000000000000000", "ssh-ed25519");
        remoteHostRepository.save(host);
        Job job = job(host, "/bin/sh -c \"echo should-not-run\"");

        JobExecutor.Outcome outcome = sshJobExecutor.execute(job, log -> {}, new CancellationToken());

        assertThat(outcome.result()).isEqualTo(JobExecutor.Result.FAILED);
    }
}
