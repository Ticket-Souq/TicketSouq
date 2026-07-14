package org.ticketsouq.apigateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.apigateway.service.AuthService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service/auth")
@RequiredArgsConstructor
public class AuthPrivateController {

    private final AuthService authService;

    @PostMapping("/unlock-org")
    public ResponseEntity<Void> unlockOrg(@RequestBody UUID orgHeadId) {
        authService.unlockOrg(orgHeadId);
        return ResponseEntity.ok().build();
    }
}
