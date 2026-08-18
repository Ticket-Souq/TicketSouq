package org.ticketsouq.auditservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.ticketsouq.sharedmodule.UserService.dto.UserEmail;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/v1/user", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface userServiceClient {

    @PostMapping("/emails")
    List<UserEmail> getUsersEmails(@RequestBody List<UUID> ids);
}
