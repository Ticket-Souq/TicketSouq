package org.ticketsouq.sharedmodule.EventService.events;

import java.util.UUID;

public record OrganizerReservationCancelledEvent(
    UUID eventId
) {}
