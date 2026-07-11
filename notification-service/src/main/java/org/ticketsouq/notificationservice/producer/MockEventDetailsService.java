package org.ticketsouq.notificationservice.producer;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.dto.EventDetailsResponse;
import org.ticketsouq.notificationservice.service.EventDetailsService;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Profile("local")
public class MockEventDetailsService implements EventDetailsService {

    @Override
    @Cacheable(value = "events", key = "#eventId")


    public EventDetailsResponse getEvent(UUID eventId) {
        System.out.println("Calling mock...");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new EventDetailsResponse(
            eventId,
            "Coldplay Concert",
            "Cairo International Stadium",
            LocalDateTime.of(2026, 8, 15, 20, 0)
        );
    }
}
