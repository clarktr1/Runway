package com.runway.api.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.runway.api.auth.Organization;
import com.runway.api.auth.OrganizationRepository;
import com.runway.api.auth.User;
import com.runway.api.auth.UserRepository;
import com.runway.api.credentials.CredentialEncryptionService;
import com.runway.api.credentials.SshCredential;
import com.runway.api.credentials.SshCredentialRepository;
import com.runway.api.executions.Execution;
import com.runway.api.executions.ExecutionLogRepository;
import com.runway.api.executions.ExecutionRepository;
import com.runway.api.executions.ExecutionService;
import com.runway.api.executions.ExecutionStatus;
import com.runway.api.executions.LogStream;
import com.runway.api.executions.TriggerType;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobRepository;
import com.runway.api.jobs.JobType;
import com.runway.api.remotehosts.RemoteHost;
import com.runway.api.remotehosts.RemoteHostRepository;
import com.runway.api.ssh.PinnedHostKeyVerifier;
import com.runway.api.ssh.SshKeyParser;
import com.runway.api.testsupport.EmbeddedSshServer;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.schmizz.sshj.SSHClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "runway.worker.enabled=true")
class WorkerIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionLogRepository executionLogRepository;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private ExecutionRunner executionRunner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SshCredentialRepository sshCredentialRepository;

    @Autowired
    private RemoteHostRepository remoteHostRepository;

    @Autowired
    private CredentialEncryptionService encryptionService;

    private Organization organization;
    private User sshTestUser;
    private EmbeddedSshServer sshServer;

    @AfterEach
    void cleanUp() throws Exception {
        if (sshServer != null) {
            sshServer.close();
        }
        if (organization != null) {
            organizationRepository.deleteById(organization.getId());
        }
        if (sshTestUser != null) {
            userRepository.deleteById(sshTestUser.getId());
        }
    }

    private Job job(JobType type, Map<String, Object> configuration) {
        return job(type, configuration, null, 1, Map.of());
    }

    private Job job(
            JobType type,
            Map<String, Object> configuration,
            Integer timeoutSeconds,
            int maxConcurrency,
            Map<String, Object> retryPolicy) {
        organization = organizationRepository.save(new Organization("Acme"));
        return jobRepository.save(new Job(
                organization,
                "Test Job",
                null,
                type,
                configuration,
                true,
                timeoutSeconds,
                maxConcurrency,
                retryPolicy,
                null,
                null));
    }

    private Execution awaitStatus(UUID executionId, ExecutionStatus... targets) throws InterruptedException {
        Set<ExecutionStatus> targetStatuses = Set.of(targets);
        // 30s budget: comfortable locally, but also covers cold-start contention (JVM warmup,
        // Redis connection pool establishing) on a shared/throttled CI runner for whichever test
        // happens to run first against a freshly-started worker.
        for (int i = 0; i < 120; i++) {
            Execution execution = executionRepository.findById(executionId).orElseThrow();
            if (targetStatuses.contains(execution.getStatus())) {
                return execution;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Execution did not reach " + targetStatuses + " within the expected time");
    }

    private Execution awaitCompletion(UUID executionId) throws InterruptedException {
        return awaitStatus(executionId, ExecutionStatus.SUCCESS, ExecutionStatus.FAILED);
    }

    @Test
    void shellJobCapturesStdoutAndSucceeds() throws Exception {
        Job job = job(JobType.SHELL, Map.of("command", "echo hello-world"));
        UUID executionId = executionService.triggerManual(job.getId(), organization.getId()).id();

        Execution execution = awaitCompletion(executionId);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getExitCode()).isEqualTo(0);
        assertThat(execution.getWorkerId()).isNotNull();

        List<String> stdout = executionLogRepository.findByExecution_IdOrderByIdAsc(executionId).stream()
                .filter(log -> log.getStream() == LogStream.STDOUT)
                .map(com.runway.api.executions.ExecutionLog::getMessage)
                .toList();
        assertThat(stdout).contains("hello-world");
    }

    @Test
    void shellJobFailsWithNonZeroExitCode() throws Exception {
        Job job = job(JobType.SHELL, Map.of("command", "exit 3"));
        UUID executionId = executionService.triggerManual(job.getId(), organization.getId()).id();

        Execution execution = awaitCompletion(executionId);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getExitCode()).isEqualTo(3);
    }

    @Test
    void httpJobSucceedsOnA2xxResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] response = "ok".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            Job job = job(
                    JobType.HTTP,
                    Map.of("method", "GET", "url", "http://localhost:" + server.getAddress().getPort() + "/ok"));
            UUID executionId =
                    executionService.triggerManual(job.getId(), organization.getId()).id();

            Execution execution = awaitCompletion(executionId);

            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(execution.getExitCode()).isEqualTo(200);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shellJobTimesOutWhenItExceedsItsTimeout() throws Exception {
        Job job = job(JobType.SHELL, Map.of("command", "sleep 5"), 1, 1, Map.of());
        UUID executionId = executionService.triggerManual(job.getId(), organization.getId()).id();

        Execution execution = awaitStatus(
                executionId, ExecutionStatus.TIMEOUT, ExecutionStatus.SUCCESS, ExecutionStatus.FAILED);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.TIMEOUT);
    }

    @Test
    void cancellingARunningExecutionStopsItQuickly() throws Exception {
        Job job = job(JobType.SHELL, Map.of("command", "sleep 30"), null, 1, Map.of());
        UUID executionId = executionService.triggerManual(job.getId(), organization.getId()).id();

        awaitStatus(executionId, ExecutionStatus.RUNNING);
        executionService.cancel(executionId, organization.getId());

        Execution execution = awaitStatus(executionId, ExecutionStatus.CANCELLED);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
    }

    @Test
    void failedExecutionWithRetryPolicyProducesAPendingRetry() throws Exception {
        Job job = job(
                JobType.SHELL,
                Map.of("command", "exit 1"),
                null,
                1,
                Map.of("maxAttempts", 2, "initialDelaySeconds", 60));
        UUID executionId = executionService.triggerManual(job.getId(), organization.getId()).id();

        awaitCompletion(executionId);

        Page<Execution> executions =
                executionRepository.search(organization.getId(), job.getId(), null, Pageable.unpaged());
        assertThat(executions.getTotalElements()).isEqualTo(2);

        Execution retry = executions.getContent().stream()
                .filter(e -> e.getTriggerType() == TriggerType.RETRY)
                .findFirst()
                .orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(ExecutionStatus.RETRYING);
        assertThat(retry.getAttempt()).isEqualTo(2);
        assertThat(retry.getNextAttemptAt()).isNotNull();
    }

    @Test
    void sshCommandJobSucceedsThroughTheFullPipeline() throws Exception {
        String privateKey =
                """
                -----BEGIN OPENSSH PRIVATE KEY-----
                b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
                QyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBgAAAJhstTV+bLU1
                fgAAAAtzc2gtZWQyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBg
                AAAED+G5biVjxsRt2rGCaDzNCtrNXgCw3RvgTwAeJuXTIeEWFxCoRdesFItoXpCZJVse1T
                uC79q5v3+KTumK8DAKIGAAAAD3J1bndheS10ZXN0LWtleQECAwQFBg==
                -----END OPENSSH PRIVATE KEY-----
                """;
        String publicKeyLine =
                "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGFxCoRdesFItoXpCZJVse1TuC79q5v3+KTumK8DAKIG runway-test-key";

        sshServer = new EmbeddedSshServer("testuser", publicKeyLine);
        organization = organizationRepository.save(new Organization("SSH Pipeline Test Org"));
        sshTestUser = userRepository.save(
                new User("ssh-pipeline-" + System.nanoTime() + "@runway.dev", "unused-hash", "SSH Pipeline User"));

        SshKeyParser.ParsedKey parsedKey = SshKeyParser.parse(privateKey, null);
        SshCredential credential = sshCredentialRepository.save(new SshCredential(
                organization,
                sshTestUser.getId(),
                "Pipeline Key",
                encryptionService.encrypt(privateKey),
                null,
                parsedKey.fingerprint(),
                parsedKey.publicKeyPreview()));

        RemoteHost host = new RemoteHost(
                organization, "Pipeline Host", sshServer.getHostname(), sshServer.getPort(), "testuser", credential);
        PinnedHostKeyVerifier verifier = new PinnedHostKeyVerifier(null);
        try (SSHClient client = new SSHClient()) {
            client.setConnectTimeout(5_000);
            client.addHostKeyVerifier(verifier);
            client.connect(host.getHostname(), host.getPort());
        }
        host.pin(verifier.getPresentedFingerprint(), verifier.getPresentedAlgorithm());
        host = remoteHostRepository.save(host);

        Job job = jobRepository.save(new Job(
                organization,
                "SSH Pipeline Job",
                null,
                JobType.SSH_COMMAND,
                Map.of("remoteHostId", host.getId().toString(), "command", "/bin/sh -c \"echo pipeline-output\""),
                true,
                null,
                1,
                Map.of(),
                null,
                null));
        UUID executionId = executionService.triggerManual(job.getId(), organization.getId()).id();

        Execution execution = awaitCompletion(executionId);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getExitCode()).isEqualTo(0);

        List<String> stdout = executionLogRepository.findByExecution_IdOrderByIdAsc(executionId).stream()
                .filter(log -> log.getStream() == LogStream.STDOUT)
                .map(com.runway.api.executions.ExecutionLog::getMessage)
                .toList();
        assertThat(stdout).contains("pipeline-output");
    }

    @Test
    void concurrencyLimitBlocksASecondExecutionWhileTheFirstIsRunning() throws Exception {
        Job job = job(JobType.SHELL, Map.of("command", "sleep 2"), null, 1, Map.of());
        Execution first = executionRepository.save(new Execution(job, TriggerType.MANUAL));
        Execution second = executionRepository.save(new Execution(job, TriggerType.MANUAL));

        Thread runFirst = new Thread(() -> executionRunner.run(first.getId(), UUID.randomUUID()));
        runFirst.start();

        awaitStatus(first.getId(), ExecutionStatus.RUNNING);

        executionRunner.run(second.getId(), UUID.randomUUID());
        Execution reloadedSecond = executionRepository.findById(second.getId()).orElseThrow();
        assertThat(reloadedSecond.getStatus()).isEqualTo(ExecutionStatus.QUEUED);

        runFirst.join();
        Execution reloadedFirst = executionRepository.findById(first.getId()).orElseThrow();
        assertThat(reloadedFirst.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    }
}
