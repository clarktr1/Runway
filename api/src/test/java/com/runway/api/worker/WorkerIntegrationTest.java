package com.runway.api.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.runway.api.auth.Organization;
import com.runway.api.auth.OrganizationRepository;
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
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private Organization organization;

    @AfterEach
    void cleanUp() {
        if (organization != null) {
            organizationRepository.deleteById(organization.getId());
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
        for (int i = 0; i < 40; i++) {
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
