package org.ticketsouq.eventservice.Client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/organizations/{organizationId}/permissions/organizer")
    boolean isOrganizer(
        @PathVariable UUID organizationId,
        @RequestHeader("X-User-Id") UUID userId
    );

    @GetMapping("/api/v1/organizations/{organizationId}/permissions/manage-event")
    boolean canManageEvent(
        @PathVariable UUID organizationId,
        @RequestHeader("X-User-Id") UUID userId
    );
}
