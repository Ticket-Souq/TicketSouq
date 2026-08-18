package org.ticketsouq.eventservice.Client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/v1/private/user", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/organization")
    String getOrganizationName(@RequestParam("id") UUID userId);

    @GetMapping("/name")
    Map<String, String> getUserNames(@RequestParam("ids") List<UUID> ids);
}
