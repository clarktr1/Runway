package com.runway.api.jobs.dto;

import com.runway.api.jobs.JobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

public record JobRequest(
        @NotBlank String name,
        String description,
        @NotNull JobType type,
        @NotNull Map<String, Object> configuration,
        boolean enabled,
        Integer timeoutSeconds,
        @Positive int maxConcurrency,
        Map<String, Object> retryPolicy,
        String cronExpression) {
}
