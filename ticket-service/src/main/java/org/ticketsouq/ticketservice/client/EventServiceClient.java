package org.ticketsouq.ticketservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketsouq.ticketservice.dto.EventSnapshotResponse;

import java.util.UUID;

@FeignClient(name = "event-service", path = "/api/v1/events", fallbackFactory = EventServiceClientFallbackFactory.class)
public interface EventServiceClient {

    @GetMapping("/{id}")
    EventSnapshotResponse getEvent(@PathVariable("id") UUID id);
}
