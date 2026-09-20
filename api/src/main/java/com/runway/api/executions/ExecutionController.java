package com.runway.api.executions;

import com.runway.api.auth.AuthPrincipal;
import com.runway.api.executions.dto.ExecutionLogResponse;
import com.runway.api.executions.dto.ExecutionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ExecutionController {

    private final ExecutionService executionService;
    private final ExecutionEventPublisher executionEventPublisher;

    public ExecutionController(ExecutionService executionService, ExecutionEventPublisher executionEventPublisher) {
        this.executionService = executionService;
        this.executionEventPublisher = executionEventPublisher;
    }

    @GetMapping("/api/executions")
    public Page<ExecutionResponse> search(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return executionService.search(principal.organizationId(), jobId, status, pageable);
    }

    @GetMapping("/api/executions/{id}")
    public ExecutionResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return executionService.get(id, principal.organizationId());
    }

    @GetMapping("/api/executions/{id}/logs")
    public List<ExecutionLogResponse> logs(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return executionService.getLogs(id, principal.organizationId()).stream()
                .map(ExecutionLogResponse::from)
                .toList();
    }

    @GetMapping(value = "/api/executions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        executionService.get(id, principal.organizationId());
        return executionEventPublisher.subscribe(id);
    }

    @PostMapping("/api/jobs/{jobId}/run")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse run(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID jobId) {
        return executionService.triggerManual(jobId, principal.organizationId());
    }

    @PostMapping("/api/executions/{id}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse retry(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return executionService.retryFromFailure(id, principal.organizationId());
    }

    @PostMapping("/api/executions/{id}/cancel")
    public ExecutionResponse cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return executionService.cancel(id, principal.organizationId());
    }
}
