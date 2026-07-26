package org.ticketsouq.userservice.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    String name,
    String email,
    String organizationName
) {}
