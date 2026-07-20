package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record RevenueByTierResponse(
    List<TierData> tiers
) {
    public record TierData(String name, double revenue, int pctOfTotal) {}
}
