package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record NoShowsByTierResponse(
    List<EventTierBreakdown> events
) {
    public record EventTierBreakdown(
        String eventId,
        String name,
        List<TierNoShow> tiers
    ) {}
    public record TierNoShow(String name, double noShowPct) {}
}
