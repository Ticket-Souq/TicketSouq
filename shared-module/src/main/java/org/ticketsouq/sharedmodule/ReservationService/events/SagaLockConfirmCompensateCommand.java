package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record SagaLockConfirmCompensateCommand(
    UUID reservationId
) {
}