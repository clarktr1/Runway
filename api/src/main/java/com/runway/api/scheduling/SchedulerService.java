package com.runway.api.scheduling;

import com.runway.api.executions.ExecutionService;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(value = "runway.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerService {

    private final JobRepository jobRepository;
    private final ExecutionService executionService;
    private final CronService cronService;

    public SchedulerService(JobRepository jobRepository, ExecutionService executionService, CronService cronService) {
        this.jobRepository = jobRepository;
        this.executionService = executionService;
        this.cronService = cronService;
    }

    @Scheduled(fixedDelayString = "${runway.scheduler.interval-ms:5000}")
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        for (UUID jobId : jobRepository.findDueJobIdsForUpdate(now)) {
            Job job = jobRepository.findById(jobId).orElseThrow();
            executionService.enqueueScheduled(job);
            Instant base = job.getNextRunAt() != null ? job.getNextRunAt() : now;
            job.recordScheduled(cronService.nextExecution(job.getCronExpression(), base));
        }
    }
}
