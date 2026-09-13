package com.runway.api.executions;

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
}
