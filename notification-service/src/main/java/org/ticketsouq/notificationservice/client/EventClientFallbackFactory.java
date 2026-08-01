package org.ticketsouq.notificationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.GeneralExceptions.RemoteServiceUnavailableException;
import org.ticketsouq.notificationservice.dto.EventDetailsResponse;

import java.util.UUID;

@Slf4j
@Component
public class EventClientFallbackFactory implements FallbackFactory<EventClient> {

    @Override
    public EventClient create(Throwable cause) {
        log.error("event-service call failed: {}", cause.getMessage());
        return new EventClient() {
            @Override
            public EventDetailsResponse getEventById(UUID eventId) {
                throw new RemoteServiceUnavailableException("event-service", "event lookup");
            }
        };
    }
}
