package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record SagaLockConfirmCommand(
    UUID reservationId
) {
}
