package org.ticketsouq.eventservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("""
            SELECT e FROM Event e
            LEFT JOIN FETCH e.sections
            WHERE e.id = :id
        """)
    Optional<Event> findEventById(@Param("id") UUID uuid);

    @Query("""
        SELECT e FROM Event e
        WHERE (:organization IS NULL AND e.status IN :statuses) OR (:organization IS NOT NULL AND e.organization = :organization)
        ORDER BY e.startDate ASC
        """)
    Page<Event> findFilteredEvents(@Param("organization") String organization, @Param("statuses") List<EventStatus> statuses, Pageable pageable);

    @Query(value = """
        SELECT e.* FROM events e
        LEFT JOIN event_categories ec ON ec.id = e.event_category_id
        WHERE (:title IS NULL OR e.title % :title)
          AND (:organization IS NULL OR e.organization % :organization)
          AND (:category IS NULL OR ec.name % :category)
        ORDER BY GREATEST(
            COALESCE(similarity(e.title, :title), 0),
            COALESCE(similarity(e.organization, :organization), 0),
            COALESCE(similarity(ec.name, :category), 0)
        ) DESC
        """, countQuery = """
        SELECT COUNT(DISTINCT e.id) FROM events e
        LEFT JOIN event_categories ec ON ec.id = e.event_category_id
        WHERE (:title IS NULL OR e.title % :title)
          AND (:organization IS NULL OR e.organization % :organization)
          AND (:category IS NULL OR ec.name % :category)
        """, nativeQuery = true)
    Page<Event> searchBy(@Param("title") String title, @Param("organization") String organization, @Param("category") String category, Pageable pageable);


}
