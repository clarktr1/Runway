package com.runway.api.worker;

import com.runway.api.queue.ExecutionQueueService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "runway.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final ExecutionQueueService executionQueueService;
    private final ExecutionRunner executionRunner;
    private final WorkerRepository workerRepository;
    private final UUID workerId = UUID.randomUUID();
    private final AtomicInteger busyCount = new AtomicInteger(0);

    private volatile boolean running = true;
    private Thread pollThread;

    public WorkerService(
            ExecutionQueueService executionQueueService,
            ExecutionRunner executionRunner,
            WorkerRepository workerRepository) {
        this.executionQueueService = executionQueueService;
        this.executionRunner = executionRunner;
        this.workerRepository = workerRepository;
    }

    @PostConstruct
    void start() {
        workerRepository.save(new Worker(workerId, WorkerStatus.HEALTHY));
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
        workerRepository.findById(workerId).ifPresent(worker -> {
            worker.heartbeat(WorkerStatus.OFFLINE);
            workerRepository.save(worker);
        });
    }

    @Scheduled(fixedDelayString = "${runway.worker.heartbeat-interval-ms:10000}")
    void heartbeat() {
        workerRepository.findById(workerId).ifPresent(worker -> {
            worker.heartbeat(busyCount.get() > 0 ? WorkerStatus.BUSY : WorkerStatus.HEALTHY);
            workerRepository.save(worker);
        });
    }

    private void pollLoop() {
        while (running) {
            try {
                executionQueueService.poll(Duration.ofSeconds(5)).ifPresent(executionId -> {
                    busyCount.incrementAndGet();
                    try {
                        executionRunner.run(executionId, workerId);
                    } finally {
                        busyCount.decrementAndGet();
                    }
                });
            } catch (Exception e) {
                if (running) {
                    log.error("Worker {} poll loop error", workerId, e);
                }
            }
        }
    }
}
