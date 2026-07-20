package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record EventTiersResponse(
    List<TierDetail> tiers
) {
    public record TierDetail(
        String tierId,
        String name,
        int sold,
        int capacity,
        double revenue,
        double noShowPct
    ) {}
}
