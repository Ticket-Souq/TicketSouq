package org.ticketsouq.paymentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
        UUID id,
        UUID eventId,
        UUID organizerId,
        String organization,
        BigDecimal amount,
        BigDecimal netAmount,
        String currency,
        String status,
        String providerTransferId,
        String stripeTransferId,
        String failureReason,
        Integer retryCount,
        Instant createdAt,
        Instant updatedAt
) {
}
