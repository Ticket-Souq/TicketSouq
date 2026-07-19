package org.ticketsouq.sharedmodule.EventService.events;

import java.time.Instant;
import java.util.UUID;

public record EventCompletedEvent(
    UUID eventId,
    Instant completedAt
) {}
