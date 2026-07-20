package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record RefundsResponse(
    String granularity,
    List<RefundPeriod> series,
    double totalRefundRatePct
) {
    public record RefundPeriod(String period, int refunds) {}
}
