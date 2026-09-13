package com.runway.api.worker;

import com.runway.api.executions.Execution;
import com.runway.api.executions.ExecutionRepository;
import com.runway.api.executions.ExecutionService;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.ExecutionStatus;
import com.runway.api.jobs.Job;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRunner.class);

    private final ExecutionRepository executionRepository;
    private final ExecutionService executionService;
    private final List<JobExecutor> executors;

    public ExecutionRunner(
            ExecutionRepository executionRepository, ExecutionService executionService, List<JobExecutor> executors) {
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.executors = executors;
    }

    public void run(UUID executionId, UUID workerId) {
        Execution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} no longer exists, skipping", executionId);
            return;
        }
        Job job = execution.getJob();
        executionService.markRunning(executionId, workerId);

        JobExecutor executor = executors.stream()
                .filter(candidate -> candidate.supports() == job.getType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No executor for job type " + job.getType()));

        List<LogLine> logLines = new CopyOnWriteArrayList<>();
        JobExecutor.Outcome outcome;
        try {
            outcome = executor.execute(job, logLines::add);
        } catch (Exception e) {
            log.error("Execution {} threw unexpectedly", executionId, e);
            outcome = new JobExecutor.Outcome(false, null, e.getMessage());
        }

        ExecutionStatus status = outcome.success() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
        executionService.complete(executionId, status, outcome.exitCode(), outcome.errorMessage(), logLines);
    }
}
