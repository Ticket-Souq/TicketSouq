package org.ticketsouq.eventservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.eventservice.model.ZoneLock;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ZoneLockRepository extends JpaRepository<ZoneLock, UUID> {

    @Query("SELECT COALESCE(SUM(zl.quantity), 0) FROM ZoneLock zl WHERE zl.zoneId = :zoneId AND zl.expiresAt > :now")
    int sumActiveQuantityByZoneId(@Param("zoneId") UUID zoneId, @Param("now") LocalDateTime now);

    Optional<ZoneLock> findByReservationId(String reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT zl FROM ZoneLock zl WHERE zl.reservationId = :reservationId")
    Optional<ZoneLock> findByReservationIdWithLock(@Param("reservationId") String reservationId);

    void deleteByReservationId(String reservationId);

    @Modifying
    @Query(value = "DELETE FROM zone_locks WHERE id IN (SELECT id FROM zone_locks WHERE expires_at < :now LIMIT :limit)", nativeQuery = true)
    int deleteByExpiresAtBefore(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
