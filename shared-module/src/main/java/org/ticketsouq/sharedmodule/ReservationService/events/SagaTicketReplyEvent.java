package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record SagaTicketReplyEvent(
    UUID reservationId,
    boolean success,
    String failReason
) {
}