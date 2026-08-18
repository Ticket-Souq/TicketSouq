package org.ticketsouq.reservationservice.mapper;

import org.springframework.stereotype.Component;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
public class ReservationMapper {

    public Reservation createReservation(BeginReservationEvent event) {
        return Reservation.builder()
            .id(event.reservationId())
            .userId(event.userId())
            .eventId(event.eventId())
            .status(ReservationStatus.PENDING)
            .build();
    }

    public ReservationContext createReservationContext(Reservation reservation, BeginReservationEvent event) {
        List<TicketReservationDto> tickets = event.tickets();
        if (tickets == null) tickets = List.of();

        BigDecimal total = tickets.stream()
            .map(t -> t.price() != null ? t.price() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ReservationContext.builder()
            .reservationId(reservation.getId())
            .userId(reservation.getUserId())
            .eventId(reservation.getEventId())
            .tickets(tickets)
            .totalAmount(total)
            .build();
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getUserId(),
            reservation.getEventId(),
            reservation.getStatus(),
            reservation.getCreatedAt(),
            reservation.getCompletedAt()
        );
    }
}
