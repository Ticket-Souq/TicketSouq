package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record ReservationCancelledEvent(
    UUID messageId,
    UUID reservationId,
    UUID customerId,
    UUID eventId,
    String reason
) {}
