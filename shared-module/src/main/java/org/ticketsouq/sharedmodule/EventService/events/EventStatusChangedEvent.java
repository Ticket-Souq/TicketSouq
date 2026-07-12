package org.ticketsouq.sharedmodule.EventService.events;

import java.util.UUID;

public record EventStatusChangedEvent(
        UUID eventId,
        String oldStatus,
        String newStatus
) {}
