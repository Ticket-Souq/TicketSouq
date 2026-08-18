package org.ticketsouq.sharedmodule.ReservationService.events;

import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCompletedEvent(
    UUID messageId,
    UUID reservationId,
    UUID userId,
    UUID eventId,
    UUID paymentId,
    BigDecimal totalAmount,
    List<TicketReservationDto> tickets,
    boolean success,
    Instant completedAt
) {
}
