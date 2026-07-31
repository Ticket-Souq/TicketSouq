package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record EventSalesTimelineResponse(
    String granularity,
    List<PeriodDataPoint> series
) {
    public record PeriodDataPoint(String period, int ticketsCumulative) {}
}
