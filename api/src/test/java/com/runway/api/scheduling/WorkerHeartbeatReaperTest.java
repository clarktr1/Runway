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
import com.runway.api.worker.Worker;
import com.runway.api.worker.WorkerRepository;
import com.runway.api.worker.WorkerStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"runway.scheduler.enabled=true", "runway.worker.offline-threshold-ms=1000"})
@Transactional
class WorkerHeartbeatReaperTest {

    @Autowired
    private WorkerHeartbeatReaper reaper;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void marksAStaleWorkerOfflineAndFailsItsRunningExecutions() {
        UUID workerId = UUID.randomUUID();
        workerRepository.saveAndFlush(new Worker(workerId, WorkerStatus.HEALTHY));
        backdateHeartbeat(workerId, Instant.now().minus(1, ChronoUnit.HOURS));

        Organization organization = organizationRepository.save(new Organization("Acme"));
        Job job = jobRepository.save(new Job(
                organization,
                "Long Job",
                null,
                JobType.SHELL,
                Map.of("command", "sleep 60"),
                true,
                null,
                1,
                Map.of(),
                null,
                null));
        Execution execution = new Execution(job, TriggerType.MANUAL);
        execution.markRunning(workerId);
        executionRepository.save(execution);

        reaper.tick();

        Worker reloadedWorker = workerRepository.findById(workerId).orElseThrow();
        assertThat(reloadedWorker.getStatus()).isEqualTo(WorkerStatus.OFFLINE);

        Execution reloadedExecution = executionRepository.findById(execution.getId()).orElseThrow();
        assertThat(reloadedExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(reloadedExecution.getErrorMessage()).contains("went offline");
    }

    @Test
    void leavesAFreshWorkerAlone() {
        UUID workerId = UUID.randomUUID();
        workerRepository.save(new Worker(workerId, WorkerStatus.HEALTHY));

        reaper.tick();

        Worker reloaded = workerRepository.findById(workerId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(WorkerStatus.HEALTHY);
    }

    private void backdateHeartbeat(UUID workerId, Instant instant) {
        jdbcTemplate.update(
                "update workers set last_heartbeat_at = ? where id = ?", Timestamp.from(instant), workerId);
    }
}
