package org.ticketsouq.apigateway.client;

import org.ticketsouq.apigateway.dto.OrgMemberResponse;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/v1/private/user")
public interface UserServiceClient {

    @PostMapping
    void registerUser(@RequestBody CreateUserRequest request);

    @GetMapping("/isbanned")
    boolean isBelongToBannedOrg(@RequestParam UUID userId);

    @PostMapping("/generate-members")
    void generateMembers(@RequestBody GenerateMembersRequest request);

    @GetMapping("/org/members")
    List<OrgMemberResponse> getOrgMembers(@RequestHeader("X-User-Id") UUID headUserId);
}
