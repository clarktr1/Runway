package com.runway.api.worker;

import com.runway.api.worker.dto.WorkerResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {

    private final WorkerRepository workerRepository;

    public WorkerController(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @GetMapping("/api/workers")
    public List<WorkerResponse> list() {
        return workerRepository.findAllByOrderByStartedAtDesc().stream()
                .map(WorkerResponse::from)
                .toList();
    }
}
