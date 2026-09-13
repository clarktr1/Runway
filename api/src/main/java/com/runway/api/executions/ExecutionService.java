package com.runway.api.executions;

import com.runway.api.common.NotFoundException;
import com.runway.api.executions.dto.ExecutionResponse;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final JobRepository jobRepository;

    public ExecutionService(ExecutionRepository executionRepository, JobRepository jobRepository) {
        this.executionRepository = executionRepository;
        this.jobRepository = jobRepository;
    }

    public Page<ExecutionResponse> search(UUID organizationId, UUID jobId, ExecutionStatus status, Pageable pageable) {
        return executionRepository.search(organizationId, jobId, status, pageable).map(ExecutionResponse::from);
    }

    @Transactional
    public ExecutionResponse triggerManual(UUID jobId, UUID organizationId) {
        Job job = jobRepository
                .findByIdAndOrganizationId(jobId, organizationId)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        Execution execution = new Execution(job, TriggerType.MANUAL);
        return ExecutionResponse.from(executionRepository.save(execution));
    }

    @Transactional
    public void enqueueScheduled(Job job) {
        executionRepository.save(new Execution(job, TriggerType.SCHEDULED));
    }
}
