package org.ticketsouq.eventservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.eventservice.model.Seat;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @Query("SELECT s FROM Seat s JOIN FETCH s.section sec JOIN FETCH sec.event e WHERE s.id = :seatId")
    Optional<Seat> findByIdWithSectionAndEvent(@Param("seatId") UUID seatId);

    @Query("SELECT s FROM Seat s JOIN FETCH s.section sec WHERE s.id IN :seatIds")
    List<Seat> findByIdsWithSection(@Param("seatIds") List<UUID> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s JOIN FETCH s.section sec JOIN FETCH sec.event e " +
           "WHERE s.id IN :seatIds AND e.id = :eventId " +
           "ORDER BY s.id ASC")
    List<Seat> findByIdInAndEventIdWithLock(@Param("seatIds") List<UUID> seatIds,
                                            @Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id IN :seatIds ORDER BY s.id ASC")
    List<Seat> findByIdInWithLock(@Param("seatIds") List<UUID> seatIds);

    List<Seat> findBySectionIdIn(List<UUID> sectionIds);
}
