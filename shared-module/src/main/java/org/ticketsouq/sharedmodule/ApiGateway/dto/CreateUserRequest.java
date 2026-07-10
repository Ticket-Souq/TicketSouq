package org.ticketsouq.sharedmodule.ApiGateway.dto;

public record CreateUserRequest (
        String name,
        String email,
        String OrganizationName
){}
