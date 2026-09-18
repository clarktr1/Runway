package com.runway.api.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.runway.api.auth.Organization;
import com.runway.api.auth.OrganizationRepository;
import com.runway.api.executions.Execution;
import com.runway.api.executions.ExecutionRepository;
import com.runway.api.executions.ExecutionStatus;
import com.runway.api.executions.TriggerType;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobRepository;
import com.runway.api.jobs.JobType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "runway.scheduler.enabled=true")
@Transactional
class RetryServiceTest {

    @Autowired
    private RetryService retryService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void promotesADueRetryExecutionToQueued() {
        Organization organization = organizationRepository.save(new Organization("Acme"));
        Job job = jobRepository.save(new Job(
                organization,
                "Flaky Job",
                null,
                JobType.SHELL,
                Map.of("command", "exit 1"),
                true,
                null,
                1,
                Map.of("maxAttempts", 3),
                null,
                null));

        Execution failed = executionRepository.save(new Execution(job, TriggerType.MANUAL));
        Execution retrying = executionRepository.save(Execution.retryOf(
                failed, TriggerType.RETRY, ExecutionStatus.RETRYING, Instant.now().minus(1, ChronoUnit.SECONDS)));

        retryService.tick();

        Execution reloaded = executionRepository.findById(retrying.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(reloaded.getNextAttemptAt()).isNull();
    }

    @Test
    void leavesARetryExecutionAloneUntilItIsDue() {
        Organization organization = organizationRepository.save(new Organization("Acme"));
        Job job = jobRepository.save(new Job(
                organization,
                "Flaky Job",
                null,
                JobType.SHELL,
                Map.of("command", "exit 1"),
                true,
                null,
                1,
                Map.of("maxAttempts", 3),
                null,
                null));

        Execution failed = executionRepository.save(new Execution(job, TriggerType.MANUAL));
        Execution retrying = executionRepository.save(Execution.retryOf(
                failed, TriggerType.RETRY, ExecutionStatus.RETRYING, Instant.now().plus(1, ChronoUnit.HOURS)));

        retryService.tick();

        Execution reloaded = executionRepository.findById(retrying.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ExecutionStatus.RETRYING);
    }
}
