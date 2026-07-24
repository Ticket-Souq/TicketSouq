package org.ticketsouq.sharedmodule.ReservationService.events;

import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;

import java.util.UUID;

public record ReservationCompleteEvent(
    UUID reservationId,
    ReservationStatus reservationStatus
) {}
