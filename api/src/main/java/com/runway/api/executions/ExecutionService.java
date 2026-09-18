package com.runway.api.executions;

import com.runway.api.common.BadRequestException;
import com.runway.api.common.NotFoundException;
import com.runway.api.executions.dto.ExecutionResponse;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobRepository;
import com.runway.api.queue.ExecutionQueueService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final JobRepository jobRepository;
    private final ExecutionQueueService executionQueueService;
    private final CancellationRegistry cancellationRegistry;

    public ExecutionService(
            ExecutionRepository executionRepository,
            ExecutionLogRepository executionLogRepository,
            JobRepository jobRepository,
            ExecutionQueueService executionQueueService,
            CancellationRegistry cancellationRegistry) {
        this.executionRepository = executionRepository;
        this.executionLogRepository = executionLogRepository;
        this.jobRepository = jobRepository;
        this.executionQueueService = executionQueueService;
        this.cancellationRegistry = cancellationRegistry;
    }

    public Page<ExecutionResponse> search(UUID organizationId, UUID jobId, ExecutionStatus status, Pageable pageable) {
        return executionRepository.search(organizationId, jobId, status, pageable).map(ExecutionResponse::from);
    }

    public ExecutionResponse get(UUID id, UUID organizationId) {
        return executionRepository
                .findByIdAndJob_OrganizationId(id, organizationId)
                .map(ExecutionResponse::from)
                .orElseThrow(() -> new NotFoundException("Execution not found"));
    }

    public List<ExecutionLog> getLogs(UUID id, UUID organizationId) {
        Execution execution = executionRepository
                .findByIdAndJob_OrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Execution not found"));
        return executionLogRepository.findByExecution_IdOrderByIdAsc(execution.getId());
    }

    @Transactional
    public ExecutionResponse triggerManual(UUID jobId, UUID organizationId) {
        Job job = jobRepository
                .findByIdAndOrganizationId(jobId, organizationId)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        Execution execution = executionRepository.save(new Execution(job, TriggerType.MANUAL));
        executionQueueService.enqueueAfterCommit(execution.getId());
        return ExecutionResponse.from(execution);
    }

    @Transactional
    public void enqueueScheduled(Job job) {
        Execution execution = executionRepository.save(new Execution(job, TriggerType.SCHEDULED));
        executionQueueService.enqueueAfterCommit(execution.getId());
    }

    @Transactional
    public void markRunning(UUID executionId, UUID workerId) {
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        execution.markRunning(workerId);
    }

    @Transactional
    public void complete(
            UUID executionId, ExecutionStatus status, Integer exitCode, String errorMessage, List<LogLine> logLines) {
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        execution.markCompleted(status, exitCode, errorMessage);
        for (LogLine logLine : logLines) {
            executionLogRepository.save(
                    new ExecutionLog(execution, logLine.stream(), logLine.message(), logLine.timestamp()));
        }
        if (status == ExecutionStatus.FAILED || status == ExecutionStatus.TIMEOUT) {
            scheduleRetryIfEligible(execution);
        }
    }

    @Transactional
    public ExecutionResponse retryFromFailure(UUID executionId, UUID organizationId) {
        Execution execution = executionRepository
                .findByIdAndJob_OrganizationId(executionId, organizationId)
                .orElseThrow(() -> new NotFoundException("Execution not found"));
        if (execution.getStatus() != ExecutionStatus.FAILED
                && execution.getStatus() != ExecutionStatus.TIMEOUT
                && execution.getStatus() != ExecutionStatus.CANCELLED) {
            throw new BadRequestException("Only a failed, timed out, or cancelled execution can be retried");
        }
        Execution retry =
                executionRepository.save(Execution.retryOf(execution, TriggerType.RETRY, ExecutionStatus.QUEUED, null));
        executionQueueService.enqueueAfterCommit(retry.getId());
        return ExecutionResponse.from(retry);
    }

    @Transactional
    public ExecutionResponse cancel(UUID executionId, UUID organizationId) {
        Execution execution = executionRepository
                .findByIdAndJob_OrganizationId(executionId, organizationId)
                .orElseThrow(() -> new NotFoundException("Execution not found"));
        switch (execution.getStatus()) {
            case QUEUED -> execution.markCancelled();
            case RUNNING -> {
                if (!cancellationRegistry.cancel(executionId)) {
                    throw new BadRequestException("Execution is not running on this worker instance");
                }
            }
            default -> throw new BadRequestException("Execution is not cancellable");
        }
        return ExecutionResponse.from(execution);
    }

    @Transactional
    public void failStaleRunningExecutionsForWorker(UUID workerId) {
        for (Execution execution : executionRepository.findByWorkerIdAndStatus(workerId, ExecutionStatus.RUNNING)) {
            complete(execution.getId(), ExecutionStatus.FAILED, null, "Worker " + workerId + " went offline", List.of());
        }
    }

    private void scheduleRetryIfEligible(Execution execution) {
        RetryPolicy policy = RetryPolicy.from(execution.getJob().getRetryPolicy());
        if (execution.getAttempt() >= policy.maxAttempts()) {
            return;
        }
        Instant nextAttemptAt = Instant.now().plus(policy.delayForNextAttempt(execution.getAttempt()));
        executionRepository.save(
                Execution.retryOf(execution, TriggerType.RETRY, ExecutionStatus.RETRYING, nextAttemptAt));
    }

    public record LogLine(LogStream stream, String message, Instant timestamp) {
    }
}
