package org.ticketsouq.userservice.dto;

import java.util.UUID;

public record OrganizationWithHeadResponse(
    UUID id,
    String name,
    String headEmail
) {}
