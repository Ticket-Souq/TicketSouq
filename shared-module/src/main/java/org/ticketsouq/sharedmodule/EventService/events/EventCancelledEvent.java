package org.ticketsouq.sharedmodule.EventService.events;

import java.util.UUID;

public record EventCancelledEvent(
        UUID eventId,
        UUID organizationId
) {}
