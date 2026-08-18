package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record SagaPaymentReplyEvent(
    UUID reservationId,
    UUID paymentId,
    boolean success,
    String failReason
) {
}