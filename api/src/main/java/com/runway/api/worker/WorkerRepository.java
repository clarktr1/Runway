package com.runway.api.worker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {
    List<Worker> findAllByOrderByStartedAtDesc();

    List<Worker> findByLastHeartbeatAtBeforeAndStatusNot(Instant cutoff, WorkerStatus status);
}
