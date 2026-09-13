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
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobRepository;
import com.runway.api.jobs.JobType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    private Organization organization;

    @AfterEach
    void cleanUp() {
        if (organization != null) {
            organizationRepository.deleteById(organization.getId());
        }
    }

    private Job job(JobType type, Map<String, Object> configuration) {
        organization = organizationRepository.save(new Organization("Acme"));
        return jobRepository.save(new Job(
                organization, "Test Job", null, type, configuration, true, null, 1, Map.of(), null, null));
    }

    private Execution awaitCompletion(UUID executionId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            Execution execution = executionRepository.findById(executionId).orElseThrow();
            if (execution.getStatus() == ExecutionStatus.SUCCESS || execution.getStatus() == ExecutionStatus.FAILED) {
                return execution;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Execution did not complete within the expected time");
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
}
