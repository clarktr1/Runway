package com.runway.api.demo;

import com.runway.api.executions.RetryPolicy;
import com.runway.api.jobs.JobType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DemoJobCatalog {

    private static final RetryPolicy NO_RETRIES = new RetryPolicy(1, "FIXED", 30, 600);

    private final List<DemoJob> jobs = List.of(
            new DemoJob(
                    "http-get",
                    JobType.HTTP,
                    Map.of("method", "GET", "url", "https://httpbin.org/get?source=runway"),
                    15,
                    NO_RETRIES),
            new DemoJob(
                    "http-post",
                    JobType.HTTP,
                    Map.of(
                            "method", "POST",
                            "url", "https://httpbin.org/post",
                            "headers", Map.of("Content-Type", "application/json"),
                            "body", "{\"job\":\"runway-demo\",\"message\":\"Hello from Runway\"}"),
                    15,
                    NO_RETRIES),
            new DemoJob(
                    "shell-live-logs",
                    JobType.SHELL,
                    Map.of(
                            "command",
                            "echo \"Starting...\"; for i in 1 2 3 4 5; do echo \"Step $i of 5\"; sleep 1; done; "
                                    + "echo \"Something worth a warning\" >&2; sleep 1; echo \"Done.\""),
                    30,
                    NO_RETRIES),
            new DemoJob(
                    "http-retry",
                    JobType.HTTP,
                    Map.of("method", "GET", "url", "https://httpbin.org/status/500"),
                    15,
                    new RetryPolicy(3, "FIXED", 5, 60)),
            new DemoJob(
                    "http-timeout",
                    JobType.HTTP,
                    Map.of("method", "GET", "url", "https://httpbin.org/delay/10"),
                    3,
                    NO_RETRIES));

    public List<DemoJob> all() {
        return jobs;
    }

    public Optional<DemoJob> find(String id) {
        return jobs.stream().filter(job -> job.id().equals(id)).findFirst();
    }
}
