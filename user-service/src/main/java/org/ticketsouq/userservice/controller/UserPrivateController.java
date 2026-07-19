package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;
import org.ticketsouq.userservice.dto.MemberSummaryResponse;
import org.ticketsouq.userservice.service.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/private/user")
@RequiredArgsConstructor
@Hidden
public class UserPrivateController {
    private final UserService userService;

    // ── Register user (called by api-gateway AuthService.register) ────────────

    @PostMapping
    public ResponseEntity<Void> register(@RequestBody CreateUserRequest request) {
        userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── Banned-org check (called by api-gateway AuthService.assertLoginAllowed) ─

    @GetMapping("/isbanned")
    public ResponseEntity<Boolean> isBelongToBannedOrg(@RequestParam UUID userId) {
        return ResponseEntity.ok(userService.isBelongToBannedOrg(userId));
    }

    // ── Generate org members (called by api-gateway AuthService.generateAccountsForOrg) ─

    // Gateway calls this AFTER creating passwords, to save the profiles here
    @PostMapping("/generate-members")
    public ResponseEntity<Void> generateMembers(@RequestBody GenerateMembersRequest request) {
        userService.generateMembers(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/organization")
    public ResponseEntity<String> getOrganizationNameByUserId(@RequestParam UUID id) {
        return ResponseEntity.ok(userService.getOrganizationNameByUserId(id));
    }

    @GetMapping("/org-head-email")
    public ResponseEntity<String> getOrgHeadEmail(@RequestParam String organizationName) {
        return ResponseEntity.ok(userService.getOrgHeadEmailByOrgName(organizationName));
    }

    @PostMapping("/members/batch")
    public ResponseEntity<List<MemberSummaryResponse>> getMembersBatch(@RequestBody List<UUID> ids) {
        return ResponseEntity.ok(userService.getMembersByIds(ids));
    }
}
