package org.ticketsouq.reservationservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import org.ticketsouq.reservationservice.model.SagaInstance;
import org.ticketsouq.reservationservice.model.enums.SagaStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SagaInstance s WHERE s.reservationId = :reservationId")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    Optional<SagaInstance> findByReservationIdForUpdate(UUID reservationId);

    Page<SagaInstance> findBySagaStatusIn(List<SagaStatus> statuses, Pageable pageable);

    List<SagaInstance> findBySagaStatusAndLastStepCompletedAtBefore(SagaStatus status, Instant timestamp);
}
