package org.ticketsouq.eventservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.eventservice.model.EventCategory;

import java.util.Optional;
import java.util.UUID;

public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {

    Optional<EventCategory> findByNameIgnoreCase(String name);
}
