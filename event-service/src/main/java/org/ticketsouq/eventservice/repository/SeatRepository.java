package org.ticketsouq.eventservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findBySection_Id(UUID sectionId);

    List<Seat> findBySection_IdAndStatus(UUID sectionId, SeatStatus status);

    void deleteBySection_Id(UUID sectionId);
}
