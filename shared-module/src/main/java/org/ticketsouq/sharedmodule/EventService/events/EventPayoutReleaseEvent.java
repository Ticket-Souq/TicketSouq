package org.ticketsouq.sharedmodule.EventService.events;

import java.time.Instant;
import java.util.UUID;

public record EventPayoutReleaseEvent(
    UUID eventId,
    String organization,
    Instant releasedAt
) {}
