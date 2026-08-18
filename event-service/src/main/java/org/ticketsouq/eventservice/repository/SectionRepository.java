package org.ticketsouq.eventservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.eventservice.model.Section;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {
    boolean existsByEventIdAndName(UUID eventId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Section s JOIN FETCH s.event e " +
        "WHERE s.id = :zoneId AND e.id = :eventId")
    Optional<Section> findByIdAndEventIdWithLock(@Param("zoneId") UUID zoneId,
                                                 @Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Section s WHERE s.id = :id")
    Optional<Section> findByIdWithLock(@Param("id") UUID id);
}
