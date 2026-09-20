package com.runway.api.executions;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED,
    RETRYING
}
