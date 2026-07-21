package org.ticketsouq.reservationservice.mapper;

import org.springframework.stereotype.Component;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;
import org.ticketsouq.sharedmodule.ReservationService.events.ReservationCreatedEvent;

import java.util.UUID;

@Component
public class ReservationMapper {

    public ReservationCreatedEvent toReservationCreatedEvent(Reservation reservation) {
        return new ReservationCreatedEvent(
            UUID.randomUUID(),
            reservation.getId(),
            reservation.getCustomerId(),
            reservation.getEventId(),
            reservation.getSeatIds(),
            reservation.getZoneId(),
            reservation.getQuantity(),
            reservation.getTotalAmount()
        );
    }

    public ReservationContext createReservationContext(Reservation reservation) {
        return ReservationContext.builder()
            .reservationId(reservation.getId())
            .customerId(reservation.getCustomerId())
            .eventId(reservation.getEventId())
            .seatIds(reservation.getSeatIds())
            .zoneId(reservation.getZoneId())
            .quantity(reservation.getQuantity())
            .totalAmount(reservation.getTotalAmount())
            .build();
    }

    public Reservation createReservation(BeginReservationEvent request) {
        return Reservation.builder()
            .customerId(request.userId())
            .eventId(request.eventId())
            .status(ReservationStatus.PENDING)
            .build();
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getCustomerId(),
            reservation.getEventId(),
            reservation.getSeatIds(),
            reservation.getZoneId(),
            reservation.getQuantity(),
            reservation.getTotalAmount(),
            reservation.getStatus(),
            reservation.getPaymentId(),
            reservation.getTicketId(),
            reservation.getCreatedAt()
        );
    }
}
