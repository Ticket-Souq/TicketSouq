package org.ticketsouq.eventservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Page<Event> findByStatusOrderByStartDateAsc(
        EventStatus status,
        Pageable pageable
    );
}
