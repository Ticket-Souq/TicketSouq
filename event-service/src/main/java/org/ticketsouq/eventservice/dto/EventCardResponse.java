package org.ticketsouq.eventservice.dto;

import lombok.Builder;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.util.UUID;

@Builder
public record EventCardResponse(
    UUID id,
    String title,
    String posterUrl,
    String bannerUrl,
    String location,
    String categoryName,
    EventStatus status,
    Instant startDate
) {
    public static EventCardResponse from(Event event) {
        return EventCardResponse.builder()
            .id(event.getId())
            .title(event.getTitle())
            .posterUrl(event.getPosterUrl())
            .bannerUrl(event.getBannerUrl())
            .location(event.getLocation())
            .categoryName(event.getEventCategory() != null ? event.getEventCategory().getName() : null)
            .status(event.getStatus())
            .startDate(event.getStartDate())
            .build();
    }
}
