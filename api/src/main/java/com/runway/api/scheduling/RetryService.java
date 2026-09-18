package com.runway.api.scheduling;

import com.runway.api.executions.Execution;
import com.runway.api.executions.ExecutionRepository;
import com.runway.api.queue.ExecutionQueueService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(value = "runway.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RetryService {

    private final ExecutionRepository executionRepository;
    private final ExecutionQueueService executionQueueService;

    public RetryService(ExecutionRepository executionRepository, ExecutionQueueService executionQueueService) {
        this.executionRepository = executionRepository;
        this.executionQueueService = executionQueueService;
    }

    @Scheduled(fixedDelayString = "${runway.scheduler.interval-ms:5000}")
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        for (UUID executionId : executionRepository.findDueRetryExecutionIdsForUpdate(now)) {
            Execution execution = executionRepository.findById(executionId).orElseThrow();
            execution.promoteToQueued();
            executionQueueService.enqueueAfterCommit(executionId);
        }
    }
}
