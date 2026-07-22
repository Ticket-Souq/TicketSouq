package org.ticketsouq.ticketservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ticketsouq.ticketservice.models.Ticket;
import org.ticketsouq.ticketservice.models.ZoneTicket;
import org.ticketsouq.ticketservice.models.SeatTicket;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByReservationId(UUID reservationId);

    List<Ticket> findByEventId(UUID eventId);

    List<Ticket> findByUserId(UUID userId);

    List<Ticket> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Ticket> findByEventIdAndReservationStatus(UUID eventId, String reservationStatus);

    List<Ticket> findByReservationStatus(String reservationStatus);

    List<Ticket> findByUserIdAndConsumed(UUID userId, boolean consumed);

    @Query("SELECT t FROM Ticket t WHERE TYPE(t) = ZoneTicket AND t.eventId = :eventId")
    List<ZoneTicket> findZoneTicketsByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT t FROM Ticket t WHERE TYPE(t) = SeatTicket AND t.eventId = :eventId")
    List<SeatTicket> findSeatTicketsByEventId(@Param("eventId") UUID eventId);
}
