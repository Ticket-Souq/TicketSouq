package org.ticketsouq.apigateway.dto;

import java.util.UUID;

public record OrgMemberWithStatus(
    UUID userId,
    String name,
    String email,
    String memberRole,
    UUID orgId,
    String organizationName,
    boolean active
) {}
