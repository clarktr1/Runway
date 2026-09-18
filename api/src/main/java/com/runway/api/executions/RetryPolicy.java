package com.runway.api.executions;

import java.time.Duration;
import java.util.Map;

public record RetryPolicy(int maxAttempts, String strategy, long initialDelaySeconds, long maxDelaySeconds) {

    private static final int DEFAULT_MAX_ATTEMPTS = 1;
    private static final String DEFAULT_STRATEGY = "FIXED";
    private static final long DEFAULT_INITIAL_DELAY_SECONDS = 30;
    private static final long DEFAULT_MAX_DELAY_SECONDS = 600;

    public static RetryPolicy from(Map<String, Object> raw) {
        return new RetryPolicy(
                intValue(raw, "maxAttempts", DEFAULT_MAX_ATTEMPTS),
                stringValue(raw, "strategy", DEFAULT_STRATEGY),
                longValue(raw, "initialDelaySeconds", DEFAULT_INITIAL_DELAY_SECONDS),
                longValue(raw, "maxDelaySeconds", DEFAULT_MAX_DELAY_SECONDS));
    }

    public Duration delayForNextAttempt(int completedAttempt) {
        long delaySeconds = "EXPONENTIAL".equalsIgnoreCase(strategy)
                ? initialDelaySeconds * (1L << Math.max(0, completedAttempt - 1))
                : initialDelaySeconds;
        return Duration.ofSeconds(Math.min(delaySeconds, maxDelaySeconds));
    }

    private static int intValue(Map<String, Object> raw, String key, int defaultValue) {
        Object value = raw == null ? null : raw.get(key);
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private static long longValue(Map<String, Object> raw, String key, long defaultValue) {
        Object value = raw == null ? null : raw.get(key);
        return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
    }

    private static String stringValue(Map<String, Object> raw, String key, String defaultValue) {
        Object value = raw == null ? null : raw.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
