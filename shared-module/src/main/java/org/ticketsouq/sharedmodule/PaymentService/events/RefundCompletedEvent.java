package org.ticketsouq.sharedmodule.PaymentService.events;

import java.util.UUID;

public record RefundCompletedEvent(
    UUID messageId,
    UUID userId,
    UUID eventId,
    Long amount
) {

}
