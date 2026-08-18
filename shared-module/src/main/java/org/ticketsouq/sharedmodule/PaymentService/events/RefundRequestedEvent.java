package org.ticketsouq.sharedmodule.PaymentService.events;

import java.util.UUID;

public record RefundRequestedEvent(
    UUID messageId,
    UUID paymentId
) {}
