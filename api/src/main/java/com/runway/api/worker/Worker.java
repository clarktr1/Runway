package com.runway.api.worker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workers")
public class Worker {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    protected Worker() {
    }

    public Worker(UUID id, WorkerStatus status) {
        this.id = id;
        this.status = status;
        Instant now = Instant.now();
        this.startedAt = now;
        this.lastHeartbeatAt = now;
    }

    public void heartbeat(WorkerStatus status) {
        this.status = status;
        this.lastHeartbeatAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }
}
