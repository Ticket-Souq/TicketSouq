package org.ticketsouq.sharedmodule.ApiGateway.dto;

import java.util.UUID;

public record CreateUserRequest (
        UUID userId,
        String name,
        String email,
        String organizationName
){}

///  organizationName = null is normal user
///  organizationName != null is orgHead  (user and organization)
///
