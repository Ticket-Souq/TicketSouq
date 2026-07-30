package org.ticketsouq.analyticsservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.ticketsouq.analyticsservice.dto.UserContextResponse;

import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/v1/private/user")
public interface UserServiceClient {

    @GetMapping("/user-context")
    UserContextResponse getUserContext(@RequestParam("userId") UUID userId);
}
