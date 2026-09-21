package com.runway.api.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.ExecutionStatus;
import com.runway.api.executions.RetryPolicy;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import com.runway.api.worker.JobExecutor;
import com.runway.api.worker.JobExecutor.Outcome;
import com.runway.api.worker.JobExecutor.Result;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DemoRunnerTest {

    private static final RetryPolicy THREE_ATTEMPTS_NO_DELAY = new RetryPolicy(3, "FIXED", 0, 0);

    private final List<DemoRunStatus> statuses = new ArrayList<>();
    private final List<LogLine> logs = new ArrayList<>();
    private final DemoRunListener listener = new DemoRunListener() {
        @Override
        public void onStatus(DemoRunStatus status) {
            statuses.add(status);
        }

        @Override
        public void onLog(LogLine line) {
            logs.add(line);
        }
    };

    private DemoJob demo(RetryPolicy retryPolicy) {
        return new DemoJob("test", JobType.HTTP, Map.of("method", "GET", "url", "http://unused"), 5, retryPolicy);
    }

    private DemoRunner runnerReturning(Outcome... outcomes) {
        Queue<Outcome> remaining = new LinkedList<>(List.of(outcomes));
        return new DemoRunner(List.of(new StubExecutor(job -> remaining.remove())));
    }

    @Test
    void reportsRunningThenSuccessForAJobThatSucceeds() {
        runnerReturning(new Outcome(Result.SUCCESS, 200, null))
                .run(demo(THREE_ATTEMPTS_NO_DELAY), listener, new CancellationToken());

        assertThat(statuses).extracting(DemoRunStatus::status)
                .containsExactly(ExecutionStatus.RUNNING, ExecutionStatus.SUCCESS);
        assertThat(statuses.get(1).exitCode()).isEqualTo(200);
    }

    @Test
    void retriesFailedAttemptsUntilOneSucceeds() {
        runnerReturning(
                        new Outcome(Result.FAILED, 500, "HTTP 500"),
                        new Outcome(Result.TIMEOUT, null, "timed out"),
                        new Outcome(Result.SUCCESS, 200, null))
                .run(demo(THREE_ATTEMPTS_NO_DELAY), listener, new CancellationToken());

        assertThat(statuses).extracting(DemoRunStatus::status)
                .containsExactly(
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.RETRYING,
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.RETRYING,
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.SUCCESS);
        assertThat(statuses.get(5).attempt()).isEqualTo(3);
        assertThat(logs).extracting(LogLine::message).anyMatch(message -> message.contains("attempt 2 of 3"));
    }

    @Test
    void givesUpAfterTheLastAttemptAndReportsItsFailure() {
        runnerReturning(
                        new Outcome(Result.FAILED, 500, "HTTP 500"),
                        new Outcome(Result.FAILED, 500, "HTTP 500"),
                        new Outcome(Result.FAILED, 500, "HTTP 500"))
                .run(demo(THREE_ATTEMPTS_NO_DELAY), listener, new CancellationToken());

        DemoRunStatus last = statuses.get(statuses.size() - 1);
        assertThat(last.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(last.attempt()).isEqualTo(3);
        assertThat(last.errorMessage()).isEqualTo("HTTP 500");
        assertThat(statuses).filteredOn(status -> status.status() == ExecutionStatus.RETRYING).hasSize(2);
    }

    @Test
    void doesNotRetryWhenTheJobHasASingleAttempt() {
        runnerReturning(new Outcome(Result.FAILED, 500, "HTTP 500"))
                .run(demo(new RetryPolicy(1, "FIXED", 0, 0)), listener, new CancellationToken());

        assertThat(statuses).extracting(DemoRunStatus::status)
                .containsExactly(ExecutionStatus.RUNNING, ExecutionStatus.FAILED);
    }

    @Test
    void treatsAnExecutorThatThrowsAsAFailedAttempt() {
        DemoRunner runner = new DemoRunner(List.of(new StubExecutor(job -> {
            throw new IllegalStateException("boom");
        })));

        runner.run(demo(new RetryPolicy(1, "FIXED", 0, 0)), listener, new CancellationToken());

        DemoRunStatus last = statuses.get(statuses.size() - 1);
        assertThat(last.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(last.errorMessage()).isEqualTo("boom");
    }

    @Test
    void stopsWaitingToRetryOnceCancelled() {
        CancellationToken cancellation = new CancellationToken();
        DemoRunner runner = new DemoRunner(List.of(new StubExecutor(job -> {
            cancellation.cancel();
            return new Outcome(Result.FAILED, 500, "HTTP 500");
        })));

        runner.run(demo(new RetryPolicy(3, "FIXED", 30, 30)), listener, cancellation);

        assertThat(statuses.get(statuses.size() - 1).status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(statuses).extracting(DemoRunStatus::status).doesNotContain(ExecutionStatus.RETRYING);
    }

    @Test
    void stopsSleepingBetweenAttemptsWhenCancelled() {
        CancellationToken cancellation = new CancellationToken();
        DemoRunner runner = new DemoRunner(List.of(new StubExecutor(job -> new Outcome(Result.FAILED, 500, "HTTP 500"))));
        DemoRunListener cancelOnRetry = new DemoRunListener() {
            @Override
            public void onStatus(DemoRunStatus status) {
                statuses.add(status);
                if (status.status() == ExecutionStatus.RETRYING) {
                    cancellation.cancel();
                }
            }

            @Override
            public void onLog(LogLine line) {
            }
        };

        long started = System.nanoTime();
        runner.run(demo(new RetryPolicy(3, "FIXED", 30, 30)), cancelOnRetry, cancellation);

        assertThat(statuses.get(statuses.size() - 1).status()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(5_000);
    }

    private record StubExecutor(java.util.function.Function<Job, Outcome> behavior) implements JobExecutor {

        @Override
        public JobType supports() {
            return JobType.HTTP;
        }

        @Override
        public Outcome execute(Job job, Consumer<LogLine> logSink, CancellationToken cancellationToken) {
            return behavior.apply(job);
        }
    }
}
