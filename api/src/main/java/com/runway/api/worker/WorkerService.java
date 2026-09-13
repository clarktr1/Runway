package com.runway.api.worker;

import com.runway.api.queue.ExecutionQueueService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "runway.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final ExecutionQueueService executionQueueService;
    private final ExecutionRunner executionRunner;
    private final UUID workerId = UUID.randomUUID();

    private volatile boolean running = true;
    private Thread pollThread;

    public WorkerService(ExecutionQueueService executionQueueService, ExecutionRunner executionRunner) {
        this.executionQueueService = executionQueueService;
        this.executionRunner = executionRunner;
    }

    @PostConstruct
    void start() {
        pollThread = new Thread(this::pollLoop, "runway-worker-" + workerId);
        pollThread.setDaemon(true);
        pollThread.start();
        log.info("Worker {} started", workerId);
    }

    @PreDestroy
    void stop() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
    }

    private void pollLoop() {
        while (running) {
            try {
                executionQueueService
                        .poll(Duration.ofSeconds(5))
                        .ifPresent(executionId -> executionRunner.run(executionId, workerId));
            } catch (Exception e) {
                if (running) {
                    log.error("Worker {} poll loop error", workerId, e);
                }
            }
        }
    }
}
