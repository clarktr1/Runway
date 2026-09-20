package com.runway.api.scheduling;

import com.runway.api.executions.ExecutionService;
import com.runway.api.worker.Worker;
import com.runway.api.worker.WorkerEventPublisher;
import com.runway.api.worker.WorkerRepository;
import com.runway.api.worker.WorkerStatus;
import com.runway.api.worker.dto.WorkerResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(value = "runway.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerHeartbeatReaper {

    private final WorkerRepository workerRepository;
    private final ExecutionService executionService;
    private final WorkerEventPublisher workerEventPublisher;
    private final long offlineThresholdMs;

    public WorkerHeartbeatReaper(
            WorkerRepository workerRepository,
            ExecutionService executionService,
            WorkerEventPublisher workerEventPublisher,
            @Value("${runway.worker.offline-threshold-ms:30000}") long offlineThresholdMs) {
        this.workerRepository = workerRepository;
        this.executionService = executionService;
        this.workerEventPublisher = workerEventPublisher;
        this.offlineThresholdMs = offlineThresholdMs;
    }

    @Scheduled(fixedDelayString = "${runway.scheduler.interval-ms:5000}")
    @Transactional
    public void tick() {
        Instant cutoff = Instant.now().minusMillis(offlineThresholdMs);
        for (Worker worker : workerRepository.findByLastHeartbeatAtBeforeAndStatusNot(cutoff, WorkerStatus.OFFLINE)) {
            worker.heartbeat(WorkerStatus.OFFLINE);
            workerEventPublisher.publish(WorkerResponse.from(worker));
            executionService.failStaleRunningExecutionsForWorker(worker.getId());
        }
    }
}
