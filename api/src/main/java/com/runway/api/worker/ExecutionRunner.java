package com.runway.api.worker;

import com.runway.api.executions.CancellationRegistry;
import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.Execution;
import com.runway.api.executions.ExecutionEventPublisher;
import com.runway.api.executions.ExecutionRepository;
import com.runway.api.executions.ExecutionService;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.ExecutionStatus;
import com.runway.api.executions.dto.ExecutionLogResponse;
import com.runway.api.jobs.Job;
import com.runway.api.queue.ExecutionQueueService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRunner.class);
    private static final long CONCURRENCY_LIMIT_RETRY_DELAY_MS = 500;

    private final ExecutionRepository executionRepository;
    private final ExecutionService executionService;
    private final ExecutionQueueService executionQueueService;
    private final CancellationRegistry cancellationRegistry;
    private final ExecutionEventPublisher executionEventPublisher;
    private final List<JobExecutor> executors;

    public ExecutionRunner(
            ExecutionRepository executionRepository,
            ExecutionService executionService,
            ExecutionQueueService executionQueueService,
            CancellationRegistry cancellationRegistry,
            ExecutionEventPublisher executionEventPublisher,
            List<JobExecutor> executors) {
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.executionQueueService = executionQueueService;
        this.cancellationRegistry = cancellationRegistry;
        this.executionEventPublisher = executionEventPublisher;
        this.executors = executors;
    }

    public void run(UUID executionId, UUID workerId) {
        Execution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} no longer exists, skipping", executionId);
            return;
        }
        if (execution.getStatus() != ExecutionStatus.QUEUED) {
            log.info("Execution {} is no longer queued ({}), skipping", executionId, execution.getStatus());
            return;
        }

        Job job = execution.getJob();
        if (executionRepository.countByJob_IdAndStatus(job.getId(), ExecutionStatus.RUNNING) >= job.getMaxConcurrency()) {
            requeueAtConcurrencyLimit(executionId);
            return;
        }

        executionService.markRunning(executionId, workerId);

        JobExecutor executor = executors.stream()
                .filter(candidate -> candidate.supports() == job.getType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No executor for job type " + job.getType()));

        List<LogLine> logLines = new CopyOnWriteArrayList<>();
        Consumer<LogLine> logSink = line -> {
            logLines.add(line);
            executionEventPublisher.publishLog(
                    executionId, new ExecutionLogResponse(line.stream(), line.message(), line.timestamp()));
        };
        CancellationToken cancellationToken = cancellationRegistry.register(executionId);
        JobExecutor.Outcome outcome;
        try {
            outcome = executor.execute(job, logSink, cancellationToken);
        } catch (Exception e) {
            log.error("Execution {} threw unexpectedly", executionId, e);
            outcome = new JobExecutor.Outcome(JobExecutor.Result.FAILED, null, e.getMessage());
        } finally {
            cancellationRegistry.unregister(executionId);
        }

        ExecutionStatus status =
                switch (outcome.result()) {
                    case SUCCESS -> ExecutionStatus.SUCCESS;
                    case FAILED -> ExecutionStatus.FAILED;
                    case TIMEOUT -> ExecutionStatus.TIMEOUT;
                    case CANCELLED -> ExecutionStatus.CANCELLED;
                };
        executionService.complete(executionId, status, outcome.exitCode(), outcome.errorMessage(), logLines);
    }

    private void requeueAtConcurrencyLimit(UUID executionId) {
        try {
            Thread.sleep(CONCURRENCY_LIMIT_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executionQueueService.enqueue(executionId);
    }
}
