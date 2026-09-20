package com.runway.api.executions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByExecution_IdOrderByIdAsc(UUID executionId);
}
