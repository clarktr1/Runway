package com.runway.api.demo;

import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.ExecutionStatus;
import com.runway.api.executions.LogStream;
import com.runway.api.jobs.Job;
import com.runway.api.worker.JobExecutor;
import com.runway.api.worker.JobExecutor.Outcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a demo job directly on the real {@link JobExecutor}s and the real
 * {@link com.runway.api.executions.RetryPolicy}, without touching the queue,
 * the database, or any account.
 */
@Service
public class DemoRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);
    private static final long CANCELLATION_POLL_MS = 100;

    private final List<JobExecutor> executors;

    public DemoRunner(List<JobExecutor> executors) {
        this.executors = executors;
    }

    /** Blocks until the run reaches a final status, reporting progress to {@code listener}. */
    public void run(DemoJob demo, DemoRunListener listener, CancellationToken cancellation) {
        // Only a carrier for the executors; it is never saved, hence no organization.
        Job job = new Job(null, demo.id(), null, demo.type(), demo.configuration(), true, demo.timeoutSeconds(), 1,
                Map.of(), null, null);
        JobExecutor executor = executors.stream()
                .filter(candidate -> candidate.supports() == demo.type())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No executor for job type " + demo.type()));
        int maxAttempts = Math.max(1, demo.retryPolicy().maxAttempts());

        for (int attempt = 1; ; attempt++) {
            listener.onStatus(DemoRunStatus.running(attempt, maxAttempts));
            Instant started = Instant.now();
            Outcome outcome = execute(executor, job, listener, cancellation);
            long durationMs = Duration.between(started, Instant.now()).toMillis();
            ExecutionStatus status = toStatus(outcome.result());

            boolean retryable = (status == ExecutionStatus.FAILED || status == ExecutionStatus.TIMEOUT)
                    && attempt < maxAttempts
                    && !cancellation.isCancelled();
            if (!retryable) {
                listener.onStatus(new DemoRunStatus(
                        status, attempt, maxAttempts, outcome.exitCode(), outcome.errorMessage(), durationMs, null));
                return;
            }

            Duration delay = demo.retryPolicy().delayForNextAttempt(attempt);
            listener.onLog(new LogLine(
                    LogStream.SYSTEM,
                    "Attempt " + attempt + " failed. Retrying in " + delay.toSeconds() + "s (attempt "
                            + (attempt + 1) + " of " + maxAttempts + ")",
                    Instant.now()));
            listener.onStatus(new DemoRunStatus(
                    ExecutionStatus.RETRYING,
                    attempt,
                    maxAttempts,
                    outcome.exitCode(),
                    outcome.errorMessage(),
                    durationMs,
                    delay.toSeconds()));
            if (!sleep(delay, cancellation)) {
                listener.onStatus(new DemoRunStatus(
                        ExecutionStatus.CANCELLED, attempt, maxAttempts, null, "Execution cancelled", null, null));
                return;
            }
        }
    }

    private Outcome execute(JobExecutor executor, Job job, DemoRunListener listener, CancellationToken cancellation) {
        try {
            return executor.execute(job, listener::onLog, cancellation);
        } catch (Exception e) {
            log.error("Demo job {} threw unexpectedly", job.getName(), e);
            return new Outcome(JobExecutor.Result.FAILED, null, e.getMessage());
        }
    }

    private ExecutionStatus toStatus(JobExecutor.Result result) {
        return switch (result) {
            case SUCCESS -> ExecutionStatus.SUCCESS;
            case FAILED -> ExecutionStatus.FAILED;
            case TIMEOUT -> ExecutionStatus.TIMEOUT;
            case CANCELLED -> ExecutionStatus.CANCELLED;
        };
    }

    /** Waits out the delay; returns false if the run was cancelled meanwhile. */
    private boolean sleep(Duration delay, CancellationToken cancellation) {
        long deadline = System.nanoTime() + delay.toNanos();
        while (System.nanoTime() < deadline) {
            if (cancellation.isCancelled()) {
                return false;
            }
            try {
                Thread.sleep(CANCELLATION_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !cancellation.isCancelled();
    }
}
