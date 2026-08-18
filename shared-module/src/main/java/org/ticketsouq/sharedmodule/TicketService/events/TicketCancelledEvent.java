package org.ticketsouq.sharedmodule.TicketService.events;

import java.util.UUID;

public record TicketCancelledEvent(
    UUID messageId,
    UUID reservationId,
    UUID customerId,
    UUID eventId
) {}
