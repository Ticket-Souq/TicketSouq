package org.ticketsouq.notificationservice.event;

import java.util.UUID;

public record PaymentSuccessEvent(
    UUID userId,
    UUID eventId,
    Long amount
) {
}
