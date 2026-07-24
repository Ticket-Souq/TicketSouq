package org.ticketsouq.reservationservice.repository;


import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxStatus status);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = org.ticketsouq.reservationservice.model.enums.OutboxStatus.IN_PROGRESS, e.claimedAt = CURRENT_TIMESTAMP WHERE e.id = :id AND e.status = org.ticketsouq.reservationservice.model.enums.OutboxStatus.PENDING")
    int markInProgress(UUID id);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = org.ticketsouq.reservationservice.model.enums.OutboxStatus.PENDING WHERE e.id = :id AND e.status = org.ticketsouq.reservationservice.model.enums.OutboxStatus.IN_PROGRESS")
    int markBackToPending(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.status = org.ticketsouq.reservationservice.model.enums.OutboxStatus.PENDING WHERE e.status = org.ticketsouq.reservationservice.model.enums.OutboxStatus.IN_PROGRESS AND e.retryCount < :maxRetries AND e.claimedAt < :staleThreshold")
    int resetStuckInProgress(int maxRetries, Instant staleThreshold);

    void deleteByStatusAndPublishedAtBefore(OutboxStatus status, Instant publishedAtBefore);
}
