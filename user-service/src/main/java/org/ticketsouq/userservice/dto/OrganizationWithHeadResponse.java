package org.ticketsouq.userservice.dto;

import org.ticketsouq.userservice.model.OrgStatus;

import java.util.UUID;

public record OrganizationWithHeadResponse(
    UUID id,
    String name,
    String headEmail,
    OrgStatus status,
    UUID orgHeadId
) {}
