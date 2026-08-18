package org.ticketsouq.analyticsservice.Client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.ticketsouq.analyticsservice.dto.UserContextResponse;

import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/v1/private/user", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/organization")
    String getOrganizationNameByUserId(@RequestParam("id") UUID userId);

    @GetMapping("/context")
    UserContextResponse getUserContext(@RequestParam("userId") UUID userId);
}
