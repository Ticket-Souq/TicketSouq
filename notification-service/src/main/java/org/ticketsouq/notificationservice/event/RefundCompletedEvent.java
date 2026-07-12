package org.ticketsouq.notificationservice.event;

import java.util.UUID;

public record RefundCompletedEvent(
    UUID messageId,
    UUID userId,
    UUID eventId,
    Long amount
) {

}
