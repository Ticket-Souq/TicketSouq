package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record ReservationCompletedEvent(
    UUID messageId,
    UUID reservationId,
    UUID customerId,
    UUID eventId,
    UUID ticketId
) {}
