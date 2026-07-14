package org.ticketsouq.userservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "api-gateway", path = "/api/v1/service/auth")
public interface AuthServiceClient {

    @PostMapping("/unlock-org")
    ResponseEntity<Void> unlockOrg(@RequestBody UUID orgHeadId);
}
