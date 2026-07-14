package org.ticketsouq.userservice.dto;

import org.ticketsouq.userservice.model.MemberRole;
import java.util.UUID;

public record OrgMemberResponse(
    UUID userId,
    String name,
    String email,
    MemberRole memberRole,
    UUID orgId,
    String organizationName
) {}
