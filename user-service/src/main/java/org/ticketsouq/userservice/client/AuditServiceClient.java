package org.ticketsouq.userservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;

@FeignClient(name = "audit-service", path = "/api/v1/private/audit")
public interface AuditServiceClient {

    @PostMapping
    void logEvent(@RequestBody AuditEvent event);
}
