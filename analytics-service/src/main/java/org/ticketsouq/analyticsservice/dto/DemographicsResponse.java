package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record DemographicsResponse(
    List<AgeGroup> ageGroups,
    List<LocationPct> topLocations
) {
    public record AgeGroup(String group, int pct) {}
    public record LocationPct(String city, int pct) {}
}
