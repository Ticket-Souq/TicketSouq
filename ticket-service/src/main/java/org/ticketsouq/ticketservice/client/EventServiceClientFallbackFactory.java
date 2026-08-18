package org.ticketsouq.ticketservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.GeneralExceptions.RemoteServiceUnavailableException;
import org.ticketsouq.ticketservice.dto.EventSnapshotResponse;

import java.util.UUID;

@Slf4j
@Component
public class EventServiceClientFallbackFactory implements FallbackFactory<EventServiceClient> {

    @Override
    public EventServiceClient create(Throwable cause) {
        log.error("event-service call failed: {}", cause.getMessage());
        return new EventServiceClient() {
            @Override
            public EventSnapshotResponse getEvent(UUID id) {
                throw new RemoteServiceUnavailableException("event-service", "event lookup");
            }
        };
    }
}
