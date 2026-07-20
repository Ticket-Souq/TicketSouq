package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record EventComparisonResponse(
    List<EventRow> events,
    int page,
    int totalPages
) {
    public record EventRow(
        String eventId,
        String name,
        String date,
        int sold,
        int capacity,
        double revenue,
        double noShowPct
    ) {}
}
