package org.ticketsouq.sharedmodule.EventService.events;

import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;

import java.util.List;
import java.util.UUID;

public record BeginReservationEvent(
    UUID eventId,
    UUID reservationId,
    UUID userId,
    List<TicketReservationDto> tickets
) {}
