package org.ticketsouq.outbox.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.ticketsouq.outbox.entity.OutboxEvent;
import org.ticketsouq.outbox.entity.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.status = 'IN_PROGRESS', e.claimedAt = CURRENT_TIMESTAMP WHERE e.id = :id AND e.status = 'PENDING'")
    int markInProgress(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.status = 'PENDING' WHERE e.status = 'IN_PROGRESS' AND e.retryCount < :maxRetries AND e.claimedAt < :staleThreshold")
    int resetStuckInProgress(int maxRetries, Instant staleThreshold);

    void deleteByStatusAndPublishedAtBefore(OutboxStatus status, Instant publishedAtBefore);
}
