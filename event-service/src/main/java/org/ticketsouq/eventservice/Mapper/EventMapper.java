package org.ticketsouq.eventservice.Mapper;

import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.dto.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;

@Component
public class EventMapper {

    public EventCardResponse toCardResponse(Event event) {
        return EventCardResponse.builder()
            .id(event.getId())
            .title(event.getTitle())
            .posterUrl(event.getPosterUrl())
            .startDate(event.getStartDate())
            .build();
    }
}
