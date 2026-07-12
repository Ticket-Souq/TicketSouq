package org.ticketsouq.sharedmodule.EventService.events;

import java.time.Instant;
import java.util.UUID;

public record EventCreatedEvent(
        UUID eventId,
        String title,
        UUID organizationId,
        String status,
        String bookingModel,
        Instant startDateTime,
        Instant endDateTime
) {}
