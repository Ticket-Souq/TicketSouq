package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record SagaLockConfirmReplyEvent(
    UUID reservationId,
    boolean success,
    String failReason
) {
}