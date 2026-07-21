package org.ticketsouq.sharedmodule.PaymentService.events;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

public record PaymentSuccessEvent(
    UUID messageId,
    UUID userId,
    UUID eventId,
    BigDecimal amount
) {
}
