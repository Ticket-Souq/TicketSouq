package org.ticketsouq.paymentservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrgPayoutSummary(
        String organization,
        BigDecimal owed,
        BigDecimal paid,
        BigDecimal outstanding,
        int eventCount,
        int payoutCount,
        List<EventPayoutRow> events
) {
}
