package org.ticketsouq.apigateway.client;


import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "user-service",path = "/api/v1/private/user")
public interface UserServiceClient {
    @PostMapping
    UUID registerUser(@RequestBody CreateUserRequest request);

}
