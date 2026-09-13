package com.runway.api.jobs;

import com.runway.api.auth.AuthPrincipal;
import com.runway.api.jobs.dto.JobRequest;
import com.runway.api.jobs.dto.JobResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public List<JobResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return jobService.list(principal.organizationId());
    }

    @GetMapping("/{id}")
    public JobResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return jobService.get(id, principal.organizationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody JobRequest request) {
        return jobService.create(principal.organizationId(), request);
    }

    @PatchMapping("/{id}")
    public JobResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody JobRequest request) {
        return jobService.update(id, principal.organizationId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        jobService.delete(id, principal.organizationId());
    }
}
