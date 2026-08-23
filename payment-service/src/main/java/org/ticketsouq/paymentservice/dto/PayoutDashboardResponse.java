package org.ticketsouq.paymentservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record PayoutDashboardResponse(
        BigDecimal totalOwed,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        List<OrgPayoutSummary> orgs
) {
}
