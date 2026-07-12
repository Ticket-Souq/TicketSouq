package org.ticketsouq.eventservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByStatusIn(List<EventStatus> statuses, Pageable pageable);

    Page<Event> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Event> findByOrganizationIdAndStatus(UUID organizationId, EventStatus status, Pageable pageable);

    Page<Event> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    List<Event> findByStartDateBeforeAndStatus(Instant dateTime, EventStatus status);

    List<Event> findByFinishDateBeforeAndStatus(Instant dateTime, EventStatus status);
}
