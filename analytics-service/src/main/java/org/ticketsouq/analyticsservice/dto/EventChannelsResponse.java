package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record EventChannelsResponse(
    List<ChannelPercentage> channels
) {
    public record ChannelPercentage(String channel, int pct) {}
}
