package org.ticketsouq.ticketservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.ticketservice.model.EventSnapshot;

import java.util.UUID;

public interface EventSnapshotRepository extends JpaRepository<EventSnapshot, UUID> {
}
