package org.ticketsouq.analyticsservice.dto;

public record ProfitResponse(
    double revenue,
    CostBreakdown costs,
    double profit,
    double marginPct
) {
    public record CostBreakdown(
        double venue,
        double staffing,
        double marketing,
        double other
    ) {}
}
