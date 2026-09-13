package com.runway.api.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.runway.api.auth.Organization;
import com.runway.api.auth.OrganizationRepository;
import com.runway.api.executions.ExecutionRepository;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobRepository;
import com.runway.api.jobs.JobType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "runway.scheduler.enabled=true")
@Transactional
class SchedulerServiceTest {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Job dueJob(Organization organization, boolean enabled) {
        Instant due = Instant.now().minus(1, ChronoUnit.MINUTES);
        return jobRepository.save(new Job(
                organization,
                "Nightly Backup",
                null,
                JobType.SHELL,
                Map.of("command", "/opt/scripts/backup.sh"),
                enabled,
                null,
                1,
                Map.of(),
                "0 * * * *",
                due));
    }

    @Test
    void enqueuesADueJobAndAdvancesItsNextRun() {
        Organization organization = organizationRepository.save(new Organization("Acme"));
        Job job = dueJob(organization, true);
        Instant previousNextRunAt = job.getNextRunAt();

        schedulerService.tick();

        Page<?> executions =
                executionRepository.search(organization.getId(), job.getId(), null, Pageable.unpaged());
        assertThat(executions.getTotalElements()).isEqualTo(1);

        Job reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getNextRunAt()).isAfter(previousNextRunAt);
    }

    @Test
    void doesNotEnqueueADisabledJob() {
        Organization organization = organizationRepository.save(new Organization("Acme"));
        Job job = dueJob(organization, false);

        schedulerService.tick();

        Page<?> executions =
                executionRepository.search(organization.getId(), job.getId(), null, Pageable.unpaged());
        assertThat(executions.getTotalElements()).isEqualTo(0);
    }
}
