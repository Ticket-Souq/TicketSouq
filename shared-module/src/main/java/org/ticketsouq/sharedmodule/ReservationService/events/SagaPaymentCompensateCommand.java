package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record SagaPaymentCompensateCommand(
    UUID reservationId,
    UUID paymentId
) {
}