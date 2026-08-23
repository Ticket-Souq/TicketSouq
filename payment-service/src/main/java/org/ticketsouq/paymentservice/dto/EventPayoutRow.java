package org.ticketsouq.paymentservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EventPayoutRow(
        UUID eventId,
        BigDecimal owed,
        BigDecimal paid,
        PayoutResponse payout
) {
}
