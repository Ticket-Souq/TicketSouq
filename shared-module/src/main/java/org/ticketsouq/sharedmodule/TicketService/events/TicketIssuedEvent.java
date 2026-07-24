package org.ticketsouq.sharedmodule.TicketService.events;

import java.util.UUID;

public record TicketIssuedEvent(
    UUID messageId,
    UUID reservationId,
    UUID customerId,
    UUID eventId,
    UUID ticketId,
    String qrCode
) {}
