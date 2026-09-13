package com.runway.api.jobs.dto;

import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String name,
        String description,
        JobType type,
        Map<String, Object> configuration,
        boolean enabled,
        Integer timeoutSeconds,
        int maxConcurrency,
        Map<String, Object> retryPolicy,
        String cronExpression,
        Instant nextRunAt,
        Instant createdAt,
        Instant updatedAt) {

    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getDescription(),
                job.getType(),
                job.getConfiguration(),
                job.isEnabled(),
                job.getTimeoutSeconds(),
                job.getMaxConcurrency(),
                job.getRetryPolicy(),
                job.getCronExpression(),
                job.getNextRunAt(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
