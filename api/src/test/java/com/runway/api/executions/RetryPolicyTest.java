package com.runway.api.executions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void defaultsToNoRetriesWhenPolicyIsEmpty() {
        RetryPolicy policy = RetryPolicy.from(Map.of());

        assertThat(policy.maxAttempts()).isEqualTo(1);
        assertThat(policy.strategy()).isEqualTo("FIXED");
        assertThat(policy.initialDelaySeconds()).isEqualTo(30);
        assertThat(policy.maxDelaySeconds()).isEqualTo(600);
    }

    @Test
    void fixedStrategyUsesTheSameDelayForEveryAttempt() {
        RetryPolicy policy = new RetryPolicy(5, "FIXED", 10, 600);

        assertThat(policy.delayForNextAttempt(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayForNextAttempt(3)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void exponentialStrategyDoublesTheDelayEachAttemptUpToTheCap() {
        RetryPolicy policy = new RetryPolicy(5, "EXPONENTIAL", 10, 90);

        assertThat(policy.delayForNextAttempt(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayForNextAttempt(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.delayForNextAttempt(3)).isEqualTo(Duration.ofSeconds(40));
        assertThat(policy.delayForNextAttempt(4)).isEqualTo(Duration.ofSeconds(80));
        assertThat(policy.delayForNextAttempt(5)).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void readsValuesFromRawMap() {
        RetryPolicy policy =
                RetryPolicy.from(Map.of("maxAttempts", 3, "strategy", "EXPONENTIAL", "initialDelaySeconds", 5, "maxDelaySeconds", 60));

        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.strategy()).isEqualTo("EXPONENTIAL");
        assertThat(policy.initialDelaySeconds()).isEqualTo(5);
        assertThat(policy.maxDelaySeconds()).isEqualTo(60);
    }
}
