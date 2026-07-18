package org.ticketsouq.sharedmodule.PaymentService.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentFailedEvent(
    UUID messageId,
    UUID userId,
    UUID eventId,
    BigDecimal amount
) {}
