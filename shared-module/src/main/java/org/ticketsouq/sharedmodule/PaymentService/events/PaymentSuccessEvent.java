package org.ticketsouq.sharedmodule.PaymentService.events;

import java.util.UUID;

public record PaymentSuccessEvent(
    UUID messageId,
    UUID userId,
    UUID eventId,
    Long amount
) {
}
