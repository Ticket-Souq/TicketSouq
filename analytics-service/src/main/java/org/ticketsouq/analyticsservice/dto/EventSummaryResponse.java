package org.ticketsouq.analyticsservice.dto;

public record EventSummaryResponse(
    String eventId,
    String name,
    String date,
    VenueInfo venue,
    int capacity,
    EventKpis kpis
) {
    public record VenueInfo(String name, String city) {}
    public record EventKpis(
        RevenueKpi revenue,
        SoldKpi sold,
        CheckInKpi checkInRate,
        RefundKpi refundRate
    ) {}
    public record RevenueKpi(double value, double deltaPctVsProjection) {}
    public record SoldKpi(int value, int capacity) {}
    public record CheckInKpi(double valuePct, double noShowPct) {}
    public record RefundKpi(double valuePct, double deltaPtVsLastEvent) {}
}
