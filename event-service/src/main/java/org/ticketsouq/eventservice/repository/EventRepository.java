package org.ticketsouq.eventservice.repository;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query(value = """
        SELECT DISTINCT e.* FROM events e
        LEFT JOIN sections s ON s.event_id = e.id
        WHERE e.title % :term OR s.name % :term
        ORDER BY GREATEST(
            similarity(e.title, :term),
            COALESCE(similarity(s.name, :term), 0)
        ) DESC
        """, countQuery = """
        SELECT COUNT(DISTINCT e.id) FROM events e
        LEFT JOIN sections s ON s.event_id = e.id
        WHERE e.title % :term OR s.name % :term
        """, nativeQuery = true)
    List<Event> findFuzzyByTitleOrSectionName(@Param("term") String term, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.status IN :statuses ORDER BY e.startDate ASC")
    Page<Event> findByStatus(@Param("statuses") List<EventStatus> statuses, Pageable pageable);

    Page<Event> findByOrganizationOrderByCreatedAtAsc(String organization, Pageable pageable);
}
