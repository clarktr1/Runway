package com.runway.api.jobs;

import com.runway.api.auth.OrganizationRepository;
import com.runway.api.common.BadRequestException;
import com.runway.api.common.NotFoundException;
import com.runway.api.jobs.dto.JobRequest;
import com.runway.api.jobs.dto.JobResponse;
import com.runway.api.remotehosts.RemoteHostRepository;
import com.runway.api.scheduling.CronService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final OrganizationRepository organizationRepository;
    private final CronService cronService;
    private final RemoteHostRepository remoteHostRepository;

    public JobService(
            JobRepository jobRepository,
            OrganizationRepository organizationRepository,
            CronService cronService,
            RemoteHostRepository remoteHostRepository) {
        this.jobRepository = jobRepository;
        this.organizationRepository = organizationRepository;
        this.cronService = cronService;
        this.remoteHostRepository = remoteHostRepository;
    }

    public List<JobResponse> list(UUID organizationId) {
        return jobRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(JobResponse::from)
                .toList();
    }

    public JobResponse get(UUID id, UUID organizationId) {
        return JobResponse.from(findOwnedJob(id, organizationId));
    }

    @Transactional
    public JobResponse create(UUID organizationId, JobRequest request) {
        validateConfiguration(organizationId, request.type(), request.configuration());
        Instant nextRunAt = computeNextRunAt(request.cronExpression());

        Job job = new Job(
                organizationRepository.getReferenceById(organizationId),
                request.name(),
                request.description(),
                request.type(),
                request.configuration(),
                request.enabled(),
                request.timeoutSeconds(),
                request.maxConcurrency(),
                request.retryPolicy() == null ? Map.of() : request.retryPolicy(),
                request.cronExpression(),
                nextRunAt);

        return JobResponse.from(jobRepository.save(job));
    }

    @Transactional
    public JobResponse update(UUID id, UUID organizationId, JobRequest request) {
        validateConfiguration(organizationId, request.type(), request.configuration());
        Instant nextRunAt = computeNextRunAt(request.cronExpression());

        Job job = findOwnedJob(id, organizationId);
        job.update(
                request.name(),
                request.description(),
                request.type(),
                request.configuration(),
                request.enabled(),
                request.timeoutSeconds(),
                request.maxConcurrency(),
                request.retryPolicy() == null ? Map.of() : request.retryPolicy(),
                request.cronExpression(),
                nextRunAt);

        return JobResponse.from(job);
    }

    private Instant computeNextRunAt(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return null;
        }
        try {
            return cronService.nextExecution(cronExpression, Instant.now());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("cronExpression is invalid: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(UUID id, UUID organizationId) {
        Job job = findOwnedJob(id, organizationId);
        jobRepository.delete(job);
    }

    private Job findOwnedJob(UUID id, UUID organizationId) {
        return jobRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Job not found"));
    }

    private void validateConfiguration(UUID organizationId, JobType type, Map<String, Object> configuration) {
        switch (type) {
            case SHELL -> requireKeys(configuration, "command");
            case HTTP -> requireKeys(configuration, "method", "url");
            case SSH_COMMAND -> {
                requireKeys(configuration, "remoteHostId", "command");
                validateRemoteHostReference(organizationId, configuration.get("remoteHostId"));
            }
        }
    }

    private void validateRemoteHostReference(UUID organizationId, Object remoteHostId) {
        UUID id;
        try {
            id = UUID.fromString(remoteHostId.toString());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("configuration.remoteHostId is not a valid id");
        }
        if (remoteHostRepository.findByIdAndOrganizationId(id, organizationId).isEmpty()) {
            throw new BadRequestException("configuration.remoteHostId does not reference a known remote host");
        }
    }

    private void requireKeys(Map<String, Object> configuration, String... keys) {
        for (String key : keys) {
            Object value = configuration.get(key);
            if (value == null || value.toString().isBlank()) {
                throw new BadRequestException("configuration." + key + " is required");
            }
        }
    }
}
