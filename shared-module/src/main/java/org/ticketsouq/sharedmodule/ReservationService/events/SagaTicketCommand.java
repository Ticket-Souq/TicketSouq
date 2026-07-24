package org.ticketsouq.sharedmodule.ReservationService.events;

import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import java.util.List;
import java.util.UUID;

public record SagaTicketCommand(
    UUID reservationId,
    UUID eventId,
    UUID userId,
    List<TicketReservationDto> tickets
) {
}