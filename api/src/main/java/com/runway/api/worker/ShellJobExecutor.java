package com.runway.api.worker;

import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.LogStream;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class ShellJobExecutor implements JobExecutor {

    private static final long UNLIMITED_TIMEOUT_SECONDS = TimeUnit.DAYS.toSeconds(365);

    @Override
    public JobType supports() {
        return JobType.SHELL;
    }

    @Override
    public Outcome execute(Job job, Consumer<LogLine> logSink, CancellationToken cancellationToken) {
        String command = String.valueOf(job.getConfiguration().get("command"));
        try {
            Process process = new ProcessBuilder("sh", "-c", command).start();
            cancellationToken.bind(process::destroyForcibly);

            Thread stdout = streamReader(process.getInputStream(), LogStream.STDOUT, logSink);
            Thread stderr = streamReader(process.getErrorStream(), LogStream.STDERR, logSink);
            stdout.start();
            stderr.start();

            long timeoutSeconds =
                    job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : UNLIMITED_TIMEOUT_SECONDS;
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished || cancellationToken.isCancelled()) {
                process.destroyForcibly();
            }
            stdout.join();
            stderr.join();

            if (cancellationToken.isCancelled()) {
                return new Outcome(Result.CANCELLED, null, "Execution cancelled");
            }
            if (!finished) {
                return new Outcome(Result.TIMEOUT, null, "Execution exceeded timeout of " + timeoutSeconds + "s");
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            return new Outcome(
                    success ? Result.SUCCESS : Result.FAILED,
                    exitCode,
                    success ? null : "Process exited with code " + exitCode);
        } catch (IOException e) {
            return new Outcome(Result.FAILED, null, "Failed to start process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(Result.FAILED, null, "Execution interrupted");
        }
    }

    private Thread streamReader(InputStream stream, LogStream logStream, Consumer<LogLine> logSink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logSink.accept(new LogLine(logStream, line, Instant.now()));
                }
            } catch (IOException ignored) {
                // stream closed once the process exits
            }
        });
        thread.setDaemon(true);
        return thread;
    }
}
