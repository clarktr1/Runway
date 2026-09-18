package com.runway.api.executions;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class CancellationRegistry {

    private final ConcurrentHashMap<UUID, CancellationToken> tokens = new ConcurrentHashMap<>();

    public CancellationToken register(UUID executionId) {
        CancellationToken token = new CancellationToken();
        tokens.put(executionId, token);
        return token;
    }

    public void unregister(UUID executionId) {
        tokens.remove(executionId);
    }

    public boolean cancel(UUID executionId) {
        CancellationToken token = tokens.get(executionId);
        if (token == null) {
            return false;
        }
        token.cancel();
        return true;
    }
}
