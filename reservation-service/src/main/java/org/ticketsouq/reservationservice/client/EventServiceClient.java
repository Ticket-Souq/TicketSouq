package org.ticketsouq.reservationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsResponse;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneResponse;

import java.util.UUID;

@FeignClient(name = "event-service", path = "/api/v1/private/events")
public interface EventServiceClient {

    @PostMapping("/{eventId}/locks/seats")
    LockSeatsResponse lockSeats(
        @PathVariable UUID eventId,
        @RequestBody LockSeatsRequest request
    );

    @PostMapping("/{eventId}/locks/zones")
    LockZoneResponse lockZone(
        @PathVariable UUID eventId,
        @RequestBody LockZoneRequest request
    );
}
