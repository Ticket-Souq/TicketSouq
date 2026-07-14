package org.ticketsouq.eventservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.eventservice.model.Seat;

import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
}
