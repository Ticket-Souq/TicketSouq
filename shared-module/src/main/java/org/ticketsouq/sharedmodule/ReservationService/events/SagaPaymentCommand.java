package org.ticketsouq.sharedmodule.ReservationService.events;

import java.math.BigDecimal;
import java.util.UUID;

public record SagaPaymentCommand(
    UUID reservationId,
    UUID userId,
    UUID eventId,
    BigDecimal amount
) {}
