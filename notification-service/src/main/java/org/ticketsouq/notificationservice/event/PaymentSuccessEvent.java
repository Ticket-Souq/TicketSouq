package org.ticketsouq.notificationservice.event;

import java.util.UUID;

public record PaymentSuccessEvent(
    UUID messageId,
    UUID userId,
    UUID eventId,
    Long amount
) {
}
