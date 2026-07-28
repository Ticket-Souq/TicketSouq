package org.ticketsouq.sharedmodule.EventService.events;

import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;

import java.util.List;
import java.util.UUID;

public record OrganizerReservationCreatedEvent(
    UUID eventId,
    UUID createdBy,
    List<TicketReservationDto> reservations
) {}
