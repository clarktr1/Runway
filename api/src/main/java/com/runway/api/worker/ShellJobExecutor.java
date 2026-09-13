package com.runway.api.worker;

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
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class ShellJobExecutor implements JobExecutor {

    @Override
    public JobType supports() {
        return JobType.SHELL;
    }

    @Override
    public Outcome execute(Job job, Consumer<LogLine> logSink) {
        String command = String.valueOf(job.getConfiguration().get("command"));
        try {
            Process process = new ProcessBuilder("sh", "-c", command).start();
            Thread stdout = streamReader(process.getInputStream(), LogStream.STDOUT, logSink);
            Thread stderr = streamReader(process.getErrorStream(), LogStream.STDERR, logSink);
            stdout.start();
            stderr.start();
            int exitCode = process.waitFor();
            stdout.join();
            stderr.join();
            boolean success = exitCode == 0;
            return new Outcome(success, exitCode, success ? null : "Process exited with code " + exitCode);
        } catch (IOException e) {
            return new Outcome(false, null, "Failed to start process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, null, "Execution interrupted");
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
