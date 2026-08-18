package org.ticketsouq.sharedmodule.ApiGateway.dto;

import java.util.List;
import java.util.UUID;

public record GenerateMembersRequest(
        UUID orgHeadUserId,
        List<MemberToCreate> members
) {
    public record MemberToCreate(UUID userId, String email, String role) {}
}
