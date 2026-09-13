package com.runway.api.executions;

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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final JobRepository jobRepository;
    private final ExecutionQueueService executionQueueService;

    public ExecutionService(
            ExecutionRepository executionRepository,
            ExecutionLogRepository executionLogRepository,
            JobRepository jobRepository,
            ExecutionQueueService executionQueueService) {
        this.executionRepository = executionRepository;
        this.executionLogRepository = executionLogRepository;
        this.jobRepository = jobRepository;
        this.executionQueueService = executionQueueService;
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
        enqueueAfterCommit(execution.getId());
        return ExecutionResponse.from(execution);
    }

    @Transactional
    public void enqueueScheduled(Job job) {
        Execution execution = executionRepository.save(new Execution(job, TriggerType.SCHEDULED));
        enqueueAfterCommit(execution.getId());
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
    }

    private void enqueueAfterCommit(UUID executionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executionQueueService.enqueue(executionId);
                }
            });
        } else {
            executionQueueService.enqueue(executionId);
        }
    }

    public record LogLine(LogStream stream, String message, Instant timestamp) {
    }
}
