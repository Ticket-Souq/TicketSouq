package org.ticketsouq.reservationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByUserId(UUID userId);
    List<Reservation> findByStatus(ReservationStatus status);
}
