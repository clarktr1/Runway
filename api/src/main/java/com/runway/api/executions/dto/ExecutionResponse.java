package com.runway.api.executions.dto;

import com.runway.api.executions.Execution;
import com.runway.api.executions.ExecutionStatus;
import com.runway.api.executions.TriggerType;
import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
        UUID id,
        UUID jobId,
        String jobName,
        UUID workerId,
        TriggerType triggerType,
        ExecutionStatus status,
        int attempt,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        Integer exitCode,
        String errorMessage,
        Long durationMs) {

    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getId(),
                execution.getJob().getId(),
                execution.getJob().getName(),
                execution.getWorkerId(),
                execution.getTriggerType(),
                execution.getStatus(),
                execution.getAttempt(),
                execution.getQueuedAt(),
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getExitCode(),
                execution.getErrorMessage(),
                execution.getDurationMs());
    }
}
