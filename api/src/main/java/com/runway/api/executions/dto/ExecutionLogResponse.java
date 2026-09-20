package com.runway.api.executions.dto;

import com.runway.api.executions.ExecutionLog;
import com.runway.api.executions.LogStream;
import java.time.Instant;

public record ExecutionLogResponse(LogStream stream, String message, Instant createdAt) {

    public static ExecutionLogResponse from(ExecutionLog log) {
        return new ExecutionLogResponse(log.getStream(), log.getMessage(), log.getCreatedAt());
    }
}
