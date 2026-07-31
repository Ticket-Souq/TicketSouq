package org.ticketsouq.analyticsservice.dto;

public record OverviewKpiResponse(
    RevenueKpi revenue,
    TicketsSoldKpi ticketsSold,
    CheckInRateKpi checkInRate,
    AvgTicketPriceKpi avgTicketPrice
) {
    public record RevenueKpi(double value, String currency, double deltaPct) {}
    public record TicketsSoldKpi(int value, int capacity) {}
    public record CheckInRateKpi(Double valuePct, Double noShowPct) {}
    public record AvgTicketPriceKpi(double value, String currency) {}
}
