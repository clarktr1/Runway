package com.runway.api.scheduling;

import com.runway.api.executions.Execution;
import com.runway.api.executions.ExecutionEventPublisher;
import com.runway.api.executions.ExecutionRepository;
import com.runway.api.executions.dto.ExecutionResponse;
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
    private final ExecutionEventPublisher executionEventPublisher;

    public RetryService(
            ExecutionRepository executionRepository,
            ExecutionQueueService executionQueueService,
            ExecutionEventPublisher executionEventPublisher) {
        this.executionRepository = executionRepository;
        this.executionQueueService = executionQueueService;
        this.executionEventPublisher = executionEventPublisher;
    }

    @Scheduled(fixedDelayString = "${runway.scheduler.interval-ms:5000}")
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        for (UUID executionId : executionRepository.findDueRetryExecutionIdsForUpdate(now)) {
            Execution execution = executionRepository.findById(executionId).orElseThrow();
            execution.promoteToQueued();
            executionEventPublisher.publishStatus(executionId, ExecutionResponse.from(execution));
            executionQueueService.enqueueAfterCommit(executionId);
        }
    }
}
