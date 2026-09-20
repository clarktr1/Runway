package com.runway.api.worker;

import com.runway.api.worker.dto.WorkerResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class WorkerController {

    private final WorkerRepository workerRepository;
    private final WorkerEventPublisher workerEventPublisher;

    public WorkerController(WorkerRepository workerRepository, WorkerEventPublisher workerEventPublisher) {
        this.workerRepository = workerRepository;
        this.workerEventPublisher = workerEventPublisher;
    }

    @GetMapping("/api/workers")
    public List<WorkerResponse> list() {
        return workerRepository.findAllByOrderByStartedAtDesc().stream()
                .map(WorkerResponse::from)
                .toList();
    }

    @GetMapping(value = "/api/workers/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return workerEventPublisher.subscribe();
    }
}
