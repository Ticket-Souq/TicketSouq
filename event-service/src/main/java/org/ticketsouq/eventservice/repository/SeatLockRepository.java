package org.ticketsouq.eventservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.eventservice.model.SeatLock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SeatLockRepository extends JpaRepository<SeatLock, UUID> {


    List<SeatLock> findBySeatIdInAndExpiresAtAfter(List<UUID> seatIds, LocalDateTime now);
    List<SeatLock> findByReservationId(String reservationId);

    void deleteByReservationId(String reservationId);

    @Modifying
    @Query(value = "DELETE FROM seat_locks WHERE id IN (SELECT id FROM seat_locks WHERE expires_at < :now LIMIT :limit)", nativeQuery = true)
    int deleteByExpiresAtBefore(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
