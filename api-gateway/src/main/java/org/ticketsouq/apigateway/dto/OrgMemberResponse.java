package org.ticketsouq.apigateway.dto;

import java.util.UUID;

public record OrgMemberResponse(
    UUID userId,
    String name,
    String email,
    String memberRole,
    UUID orgId,
    String organizationName
) {}
