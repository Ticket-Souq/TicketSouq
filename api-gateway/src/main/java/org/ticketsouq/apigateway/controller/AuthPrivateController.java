package org.ticketsouq.apigateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.apigateway.service.AuthService;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateAccountRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GeneratedAccount;

import java.util.List;
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

    @PostMapping("/generate-accounts")
    public ResponseEntity<List<GeneratedAccount>> generateAccounts(@RequestBody GenerateAccountRequest req) {
        return ResponseEntity.ok(authService.generateAccountsForOrg(req));
    }
}
