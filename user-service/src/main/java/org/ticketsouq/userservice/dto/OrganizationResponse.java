package org.ticketsouq.userservice.dto;

import org.ticketsouq.userservice.model.OrgStatus;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
    UUID id,
    String name,
    OrgStatus status,
    Instant createdAt
) {}
