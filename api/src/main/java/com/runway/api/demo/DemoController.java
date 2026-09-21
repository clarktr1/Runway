package com.runway.api.demo;

import com.runway.api.common.NotFoundException;
import com.runway.api.common.TooManyRequestsException;
import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.executions.dto.ExecutionLogResponse;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unauthenticated on purpose: it can only run the fixed jobs in
 * {@link DemoJobCatalog}, takes no input beyond which one to run, and stores
 * nothing.
 */
@RestController
@RequestMapping("/api/demo/jobs")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);
    private static final int MAX_CONCURRENT_RUNS = 5;
    private static final long RUN_TIMEOUT_MS = 120_000;

    private final DemoJobCatalog catalog;
    private final DemoRunner runner;
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_RUNS);
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "runway-demo");
        thread.setDaemon(true);
        return thread;
    });

    public DemoController(DemoJobCatalog catalog, DemoRunner runner) {
        this.catalog = catalog;
        this.runner = runner;
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    @GetMapping
    public List<DemoJob> list() {
        return catalog.all();
    }

    @PostMapping(value = "/{id}/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable String id) {
        DemoJob demo = catalog.find(id).orElseThrow(() -> new NotFoundException("Demo job not found"));
        if (!slots.tryAcquire()) {
            throw new TooManyRequestsException("The demo is busy right now. Try again in a few seconds.");
        }

        SseEmitter emitter = new SseEmitter(RUN_TIMEOUT_MS);
        CancellationToken cancellation = new CancellationToken();
        // A visitor who closes the tab or times out stops the run.
        emitter.onCompletion(cancellation::cancel);
        emitter.onTimeout(cancellation::cancel);
        emitter.onError(error -> cancellation.cancel());

        DemoRunListener listener = new DemoRunListener() {
            @Override
            public void onStatus(DemoRunStatus status) {
                send(emitter, cancellation, "status", status);
            }

            @Override
            public void onLog(LogLine line) {
                send(emitter, cancellation, "log", new ExecutionLogResponse(line.stream(), line.message(), line.timestamp()));
            }
        };

        try {
            executor.execute(() -> {
                try {
                    runner.run(demo, listener, cancellation);
                    emitter.complete();
                } catch (Exception e) {
                    log.error("Demo run {} failed", id, e);
                    emitter.completeWithError(e);
                } finally {
                    slots.release();
                }
            });
        } catch (RuntimeException e) {
            slots.release();
            throw e;
        }
        return emitter;
    }

    // Log lines arrive from several threads (stdout, stderr, the runner), so sends are serialized.
    private void send(SseEmitter emitter, CancellationToken cancellation, String event, Object data) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException e) {
                cancellation.cancel();
            }
        }
    }
}
