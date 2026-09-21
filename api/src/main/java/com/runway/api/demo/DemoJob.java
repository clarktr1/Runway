package com.runway.api.demo;

import com.runway.api.executions.RetryPolicy;
import com.runway.api.jobs.JobType;
import java.util.Map;

/**
 * A job that anonymous visitors may run. Definitions live in code
 * ({@link DemoJobCatalog}); nothing about them comes from a request, and they
 * are never persisted.
 */
public record DemoJob(
        String id, JobType type, Map<String, Object> configuration, Integer timeoutSeconds, RetryPolicy retryPolicy) {
}
