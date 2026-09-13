package com.runway.api.worker;

import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.LogStream;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class HttpJobExecutor implements JobExecutor {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public JobType supports() {
        return JobType.HTTP;
    }

    @Override
    public Outcome execute(Job job, Consumer<LogLine> logSink) {
        Map<String, Object> configuration = job.getConfiguration();
        String method = String.valueOf(configuration.getOrDefault("method", "GET")).toUpperCase();
        String url = String.valueOf(configuration.get("url"));
        Object bodyConfig = configuration.get("body");
        String body = bodyConfig != null ? String.valueOf(bodyConfig) : "";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .method(
                        method,
                        body.isEmpty()
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body));

        if (configuration.get("headers") instanceof Map<?, ?> headers) {
            headers.forEach((key, value) -> requestBuilder.header(String.valueOf(key), String.valueOf(value)));
        }

        logSink.accept(new LogLine(LogStream.SYSTEM, method + " " + url, Instant.now()));
        try {
            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            logSink.accept(new LogLine(LogStream.STDOUT, "HTTP " + response.statusCode(), Instant.now()));
            if (!response.body().isBlank()) {
                logSink.accept(new LogLine(LogStream.STDOUT, response.body(), Instant.now()));
            }
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new Outcome(success, response.statusCode(), success ? null : "HTTP " + response.statusCode());
        } catch (IOException e) {
            logSink.accept(new LogLine(LogStream.SYSTEM, "Request failed: " + e.getMessage(), Instant.now()));
            return new Outcome(false, null, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, null, "Execution interrupted");
        }
    }
}
