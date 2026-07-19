package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.userservice.client.AuthServiceClient;
import org.ticketsouq.userservice.dto.OrganizationWithHeadResponse;
import org.ticketsouq.userservice.model.OrgStatus;
import org.ticketsouq.userservice.service.OrganizationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "Manages user accounts.")
public class UserPublicController {

    private final AuthServiceClient authServiceClient;
    private final OrganizationService orgService;

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

    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationWithHeadResponse>> getAllOrganizations() {
        return ResponseEntity.ok(orgService.getAllOrganizations());
    }
}
