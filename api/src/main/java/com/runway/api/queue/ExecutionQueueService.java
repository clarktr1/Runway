package com.runway.api.queue;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExecutionQueueService {

    private static final String QUEUE_KEY = "runway:executions:queue";

    private final StringRedisTemplate redisTemplate;

    public ExecutionQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void enqueue(UUID executionId) {
        redisTemplate.opsForList().leftPush(QUEUE_KEY, executionId.toString());
    }

    public Optional<UUID> poll(Duration timeout) {
        String value = redisTemplate.opsForList().rightPop(QUEUE_KEY, timeout);
        return Optional.ofNullable(value).map(UUID::fromString);
    }
}
