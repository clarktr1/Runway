package com.runway.api.executions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    Optional<Execution> findByIdAndJob_OrganizationId(UUID id, UUID organizationId);

    @Query("select e from Execution e where e.job.organization.id = :organizationId "
            + "and (:jobId is null or e.job.id = :jobId) "
            + "and (:status is null or e.status = :status) "
            + "order by e.queuedAt desc")
    Page<Execution> search(
            @Param("organizationId") UUID organizationId,
            @Param("jobId") UUID jobId,
            @Param("status") ExecutionStatus status,
            Pageable pageable);

    long countByJob_IdAndStatus(UUID jobId, ExecutionStatus status);

    List<Execution> findByWorkerIdAndStatus(UUID workerId, ExecutionStatus status);

    @Query(
            value = "select id from executions "
                    + "where status = 'RETRYING' and next_attempt_at <= :now "
                    + "order by next_attempt_at for update skip locked",
            nativeQuery = true)
    List<UUID> findDueRetryExecutionIdsForUpdate(@Param("now") Instant now);
}
