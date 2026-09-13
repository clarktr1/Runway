package com.runway.api.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<Job> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(
            value = "select id from jobs "
                    + "where enabled = true and cron_expression is not null and next_run_at <= :now "
                    + "order by next_run_at for update skip locked",
            nativeQuery = true)
    List<UUID> findDueJobIdsForUpdate(@Param("now") Instant now);
}
