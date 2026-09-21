package com.runway.api.demo;

import com.runway.api.executions.ExecutionStatus;

/**
 * Where a demo run is right now. {@code retryInSeconds} is only set while
 * {@code status} is RETRYING; the result fields are only set once an attempt
 * has finished.
 */
public record DemoRunStatus(
        ExecutionStatus status,
        int attempt,
        int maxAttempts,
        Integer exitCode,
        String errorMessage,
        Long durationMs,
        Long retryInSeconds) {

    static DemoRunStatus running(int attempt, int maxAttempts) {
        return new DemoRunStatus(ExecutionStatus.RUNNING, attempt, maxAttempts, null, null, null, null);
    }
}
