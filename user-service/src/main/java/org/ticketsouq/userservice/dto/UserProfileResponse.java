package org.ticketsouq.userservice.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String name,
    String email,
    String organizationName,
    String memberRole,
    Instant createdAt
) {}
