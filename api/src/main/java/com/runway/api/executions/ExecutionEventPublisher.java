package com.runway.api.executions;

import com.runway.api.executions.dto.ExecutionLogResponse;
import com.runway.api.executions.dto.ExecutionResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ExecutionEventPublisher {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID executionId) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(executionId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    public void publishStatus(UUID executionId, ExecutionResponse response) {
        send(executionId, "execution", response);
    }

    public void publishLog(UUID executionId, ExecutionLogResponse logLine) {
        send(executionId, "log", logLine);
    }

    public void complete(UUID executionId) {
        List<SseEmitter> list = emitters.remove(executionId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            emitter.complete();
        }
    }

    private void send(UUID executionId, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(executionId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                list.remove(emitter);
            }
        }
    }
}
