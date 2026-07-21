package org.ticketsouq.sharedmodule.EventService.events;

import java.time.Instant;
import java.util.UUID;

public record EventCancelledEvent(
    UUID messageId,
    UUID eventId,
    UUID organizationId,
    Instant cancelledAt
) {
}
