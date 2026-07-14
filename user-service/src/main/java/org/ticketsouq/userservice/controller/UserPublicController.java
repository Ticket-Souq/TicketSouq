package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateAccountRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GeneratedAccount;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.userservice.client.AuthServiceClient;
import org.ticketsouq.userservice.model.OrgStatus;
import org.ticketsouq.userservice.service.OrganizationService;
import org.ticketsouq.userservice.service.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "Manages user accounts.")
public class UserPublicController {

    private final AuthServiceClient authServiceClient;
    private final OrganizationService orgService;
    private final UserService userService;

    // ── Org approval / banning ────────────────────────────────────────────────

    @PostMapping("/org/{orgHeadId}/approve")
    public ResponseEntity<Void> approveOrg(
        @RequestHeader(value = "X-User-Id") UUID adminId,
        @PathVariable UUID orgHeadId) {
        authServiceClient.unlockOrg(orgHeadId);
        orgService.changeStatus(orgHeadId, OrgStatus.APPROVED, adminId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/org/{orgHeadId}/ban")
    public ResponseEntity<Void> banOrg(
        @RequestHeader(value = "X-User-Id") UUID adminId,
        @PathVariable UUID orgHeadId) {

        orgService.changeStatus(orgHeadId, OrgStatus.BANNED, adminId);

        return ResponseEntity.ok().build();
    }


    // ── Account generation (org head) ─────────────────────────────────────────
        ///  ASK
    // TODO implement account generation for org:
    //  1. Make rule only ORG_HEAD can call this
    //  2. Call authServiceClient.generateAccounts(req) to create AuthCredentials
    //     → returns List<GeneratedAccount> with userId, email, password, role
    //  3. For each generated account, create a Member record in the org
    //     (or this can be handled by the existing /private/user/generate-members)
    //  4. Return the credentials so the org head can distribute them

//    @PostMapping("/org/generate-accounts")
//    public ResponseEntity<List<GeneratedAccount>> generateAccounts(@RequestBody GenerateAccountRequest req) {
//        List<GeneratedAccount> accounts = authServiceClient.generateAccounts(req).getBody();
//        // memberService.createMembersFromGeneratedAccounts(orgHeadId, accounts);
//        // return ResponseEntity.ok(accounts);
//        return ResponseEntity.ok(List.of());
//    }


    /// ── Account generation (org head) ─────────────────────────────────────────

    /**
     * ⚠️ ARCHITECTURAL WARNING: CIRCULAR DEPENDENCY RISK
     * This endpoint calls api-gateway (authServiceClient.generateAccounts),
     * and the api-gateway internally calls back to user-service (/private/user/generate-members).
     * This creates a Ping-Pong effect (User -> Gateway -> User) which can lead to
     * thread starvation and distributed deadlocks under heavy load.
     */

    @PostMapping("/org/generate-accounts")
    public ResponseEntity<List<GeneratedAccount>> generateAccounts(
        @RequestHeader(value = "X-User-Id") UUID orgHeadId,
        @RequestBody GenerateAccountRequest req) {


        List<GeneratedAccount> accounts = authServiceClient.generateAccounts(req).getBody();

        return ResponseEntity.ok(accounts);
    }
}
