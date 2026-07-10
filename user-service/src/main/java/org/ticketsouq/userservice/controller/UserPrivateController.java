package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/private/user")
@RequiredArgsConstructor
@Hidden
public class UserPrivateController {

    // TODO implement this
    @PostMapping
    public ResponseEntity<UUID> register(@RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }
}
