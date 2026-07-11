package org.ticketsouq.notificationservice.service.impl;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.client.EventClient;
import org.ticketsouq.notificationservice.dto.EventDetailsResponse;
import org.ticketsouq.notificationservice.service.EventDetailsService;

import java.util.UUID;

@Service
@Profile("!local")

public class EventDetailsServiceImpl implements EventDetailsService {

    private final EventClient eventClient;

    public EventDetailsServiceImpl(EventClient eventClient) {
        this.eventClient = eventClient;
    }

    @Override
    @Cacheable(value = "events", key = "#eventId")
    public EventDetailsResponse getEvent(UUID eventId) {
        System.out.println("recived");

        EventDetailsResponse response = eventClient.getEventById(eventId);

        return new EventDetailsResponse(
            response.id(),
            response.name(),
            response.location(),
            response.startDate()
        );
    }
}
