package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;
import org.ticketsouq.userservice.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/private/user")
@RequiredArgsConstructor
@Hidden
public class UserPrivateController {
    private final UserService userService;

    // ── Register user (called by api-gateway AuthService.register) ────────────

    // TODO implement register(CreateUserRequest):
    //  Called via Feign from api-gateway when a new user registers.
    //
    //  When request.OrganizationName == null (normal CUSTOMER):
    //    1. Create a User record (userId, name, email) in the user table
    //    2. Return 201 Created
    //
    //  When request.OrganizationName != null (ORG_HEAD):
    //    1. Look up Organization by name
    //    2. If org exists → return error
    //    3. If org does NOT exist → create Organization, then User, then Member
    //    4. Return 201 Created
    //
    //  On any failure → throw BusinessException
    @PostMapping
    public ResponseEntity<Void> register(@RequestBody CreateUserRequest request) {
        userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── Banned-org check (called by api-gateway AuthService.assertLoginAllowed) ─

    // TODO implement isBelongToBannedOrg(UUID):
    //  1. Look up the User by userId to find their organization
    //  2. Check the Organization's status
    //  3. Return true if the Organization status == BANNED
    //  4. Return false if the user has no org or the org is not banned
    @GetMapping("/isbanned")
    public ResponseEntity<Boolean> isBelongToBannedOrg(@RequestParam UUID userId) {
        return ResponseEntity.ok(userService.isBelongToBannedOrg(userId));
    }

    // ── Generate org members (called by api-gateway AuthService.generateAccountsForOrg) ─

    // TODO implement generateMembers(GenerateMembersRequest):
    //  Called via Feign from api-gateway after creating AuthCredentials.
    //  Each MemberToCreate in the request has: userId, email, role.
    //
    //  1. Look up the Organization owned by request.orgHeadUserId
    //  2. For each MemberToCreate:
    //     a. Create a User record (userId, email, name=email) in the user table
    //     b. Create a Member record (userId, orgId, role) linking them to the org
    //  3. Return 200 OK when all members are created
    //  4. On any failure → throw BusinessException (the caller should roll back)

    // Gateway calls this AFTER creating passwords, to save the profiles here
    @PostMapping("/generate-members")
    public ResponseEntity<Void> generateMembers(@RequestBody GenerateMembersRequest request) {
        userService.generateMembers(request);
        return ResponseEntity.ok().build();
    }
    ///  add endpoint to find userby id
}
