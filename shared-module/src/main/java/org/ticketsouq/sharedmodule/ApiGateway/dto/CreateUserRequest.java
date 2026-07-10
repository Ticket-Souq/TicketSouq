package org.ticketsouq.sharedmodule.ApiGateway.dto;

public record CreateUserRequest (
        String name,
        String email,
        String OrganizationName
){}

///  organizationName = null is normal user
///  organizationName != null is orgHead  (user and organization)
///
