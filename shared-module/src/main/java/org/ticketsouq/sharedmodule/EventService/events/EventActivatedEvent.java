package org.ticketsouq.sharedmodule.EventService.events;

import java.time.Instant;
import java.util.UUID;

public record EventActivatedEvent(
    UUID eventId,
    Instant activatedAt
) {}
