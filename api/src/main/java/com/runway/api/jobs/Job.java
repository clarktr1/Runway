package com.runway.api.jobs;

import com.runway.api.auth.Organization;
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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "max_concurrency", nullable = false)
    private int maxConcurrency = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_policy", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> retryPolicy;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
    }

    public Job(
            Organization organization,
            String name,
            String description,
            JobType type,
            Map<String, Object> configuration,
            boolean enabled,
            Integer timeoutSeconds,
            int maxConcurrency,
            Map<String, Object> retryPolicy,
            String cronExpression,
            Instant nextRunAt) {
        this.organization = organization;
        this.name = name;
        this.description = description;
        this.type = type;
        this.configuration = configuration;
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
        this.maxConcurrency = maxConcurrency;
        this.retryPolicy = retryPolicy;
        this.cronExpression = cronExpression;
        this.nextRunAt = nextRunAt;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name,
            String description,
            JobType type,
            Map<String, Object> configuration,
            boolean enabled,
            Integer timeoutSeconds,
            int maxConcurrency,
            Map<String, Object> retryPolicy,
            String cronExpression,
            Instant nextRunAt) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.configuration = configuration;
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
        this.maxConcurrency = maxConcurrency;
        this.retryPolicy = retryPolicy;
        this.cronExpression = cronExpression;
        this.nextRunAt = nextRunAt;
        this.updatedAt = Instant.now();
    }

    public void recordScheduled(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JobType getType() {
        return type;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public Map<String, Object> getRetryPolicy() {
        return retryPolicy;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
