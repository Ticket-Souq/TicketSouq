package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record SalesPaceResponse(
    String granularity,
    List<DataPoint> series
) {
    public record DataPoint(String date, int ticketsCumulative) {}
}
