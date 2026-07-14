package org.ticketsouq.eventservice.dto.FrontendMap;

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
    EventStatus status,
    Instant startDate
) {
    public static EventCardResponse from(Event event) {
        return EventCardResponse.builder()
            .id(event.getId())
            .title(event.getTitle())
            .posterUrl(event.getPosterUrl())
            .status(event.getStatus())
            .startDate(event.getStartDate())
            .build();
    }
}
