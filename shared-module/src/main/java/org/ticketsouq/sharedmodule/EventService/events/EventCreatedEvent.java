package org.ticketsouq.sharedmodule.EventService.events;

import java.time.Instant;
import java.util.UUID;

public record EventCreatedEvent(
        UUID eventId,
        String title,
        String organization,
        UUID createdBy,
        String bookingModel,
        Instant startDateTime,
        Instant endDateTime
) {}
