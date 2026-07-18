package org.ticketsouq.sharedmodule.PaymentService.events;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundCompletedEvent(
    UUID messageId,
    UUID userId,
    UUID eventId,
    BigDecimal amount
) {

}
