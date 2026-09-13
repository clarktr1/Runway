package com.runway.api.executions;

import com.runway.api.jobs.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "worker_id")
    private UUID workerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected Execution() {
    }

    public Execution(Job job, TriggerType triggerType) {
        this.job = job;
        this.triggerType = triggerType;
        this.status = ExecutionStatus.QUEUED;
        this.attempt = 1;
        this.queuedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
