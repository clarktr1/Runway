package com.runway.api.worker.dto;

import com.runway.api.worker.Worker;
import com.runway.api.worker.WorkerStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkerResponse(UUID id, WorkerStatus status, Instant startedAt, Instant lastHeartbeatAt) {

    public static WorkerResponse from(Worker worker) {
        return new WorkerResponse(worker.getId(), worker.getStatus(), worker.getStartedAt(), worker.getLastHeartbeatAt());
    }
}
