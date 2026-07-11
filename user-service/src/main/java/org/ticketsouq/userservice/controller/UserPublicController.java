package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateAccountRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GeneratedAccount;
import org.ticketsouq.userservice.client.AuthServiceClient;


import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "Manages user accounts.")
public class UserPublicController {

    private final AuthServiceClient authServiceClient;

    // ── Org approval / banning ────────────────────────────────────────────────

    // TODO implement org approval:
    //  1. Make rule only ADMIN can call this
    //  2. Call authServiceClient.unlockOrg(orgHeadId) to unlock the org head's auth credential
    //  3. Update Organization status from PENDING → APPROVED in local DB
    //  4. Send audit/notification event
    @PostMapping("/org/{orgHeadId}/approve")
    public ResponseEntity<Void> approveOrg(@PathVariable UUID orgHeadId) {
        authServiceClient.unlockOrg(orgHeadId);
        // orgService.changeStatus(orgHeadId, OrgStatus.APPROVED);
        return ResponseEntity.ok().build();
    }

    // TODO implement org banning:
    //  1. Make rule only ADMIN can call this
    //  2. Update Organization status to BANNED in local DB
    //  3. (Auth side: isBelongToBannedOrg() prevents logins for members of this org)
    @PostMapping("/org/{orgHeadId}/ban")
    public ResponseEntity<Void> banOrg(@PathVariable UUID orgHeadId) {
        // orgService.changeStatus(orgHeadId, OrgStatus.BANNED);
        return ResponseEntity.ok().build();
    }

    // ── Account generation (org head) ─────────────────────────────────────────

    // TODO implement account generation for org:
    //  1. Make rule only ORG_HEAD can call this
    //  2. Call authServiceClient.generateAccounts(req) to create AuthCredentials
    //     → returns List<GeneratedAccount> with userId, email, password, role
    //  3. For each generated account, create a Member record in the org
    //     (or this can be handled by the existing /private/user/generate-members)
    //  4. Return the credentials so the org head can distribute them
    @PostMapping("/org/generate-accounts")
    public ResponseEntity<List<GeneratedAccount>> generateAccounts(@RequestBody GenerateAccountRequest req) {
         List<GeneratedAccount> accounts = authServiceClient.generateAccounts(req).getBody();
        // memberService.createMembersFromGeneratedAccounts(orgHeadId, accounts);
        // return ResponseEntity.ok(accounts);
        return ResponseEntity.ok(List.of());
    }

}
