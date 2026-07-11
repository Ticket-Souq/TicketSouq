package org.ticketsouq.notificationservice.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketsouq.notificationservice.dto.EventDetailsResponse;

import java.util.UUID;

@FeignClient(
    name = "event-service"
)
@Retry(name = "event-service")

public interface EventClient {

    @GetMapping("/api/v1/events/{eventId}")
    EventDetailsResponse getEventById(@PathVariable UUID eventId);

}
