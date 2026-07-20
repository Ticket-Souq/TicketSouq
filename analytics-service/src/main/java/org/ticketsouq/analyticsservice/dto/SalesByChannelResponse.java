package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record SalesByChannelResponse(
    List<ChannelData> channels
) {
    public record ChannelData(String channel, int tickets) {}
}
