package com.runway.api.worker;

import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.LogStream;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    public Outcome execute(Job job, Consumer<LogLine> logSink, CancellationToken cancellationToken) {
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

        CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        cancellationToken.bind(() -> future.cancel(true));

        try {
            HttpResponse<String> response = job.getTimeoutSeconds() != null
                    ? future.get(job.getTimeoutSeconds(), TimeUnit.SECONDS)
                    : future.get();
            logSink.accept(new LogLine(LogStream.STDOUT, "HTTP " + response.statusCode(), Instant.now()));
            if (!response.body().isBlank()) {
                logSink.accept(new LogLine(LogStream.STDOUT, response.body(), Instant.now()));
            }
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new Outcome(
                    success ? Result.SUCCESS : Result.FAILED,
                    response.statusCode(),
                    success ? null : "HTTP " + response.statusCode());
        } catch (TimeoutException e) {
            future.cancel(true);
            logSink.accept(new LogLine(LogStream.SYSTEM, "Request exceeded timeout", Instant.now()));
            return new Outcome(Result.TIMEOUT, null, "Request exceeded timeout of " + job.getTimeoutSeconds() + "s");
        } catch (CancellationException e) {
            return new Outcome(Result.CANCELLED, null, "Execution cancelled");
        } catch (ExecutionException e) {
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            logSink.accept(new LogLine(LogStream.SYSTEM, "Request failed: " + message, Instant.now()));
            return new Outcome(Result.FAILED, null, message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(Result.FAILED, null, "Execution interrupted");
        }
    }
}
