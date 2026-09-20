package com.runway.api.worker;

import com.runway.api.worker.dto.WorkerResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WorkerEventPublisher {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    public void publish(WorkerResponse worker) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("worker").data(worker));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
