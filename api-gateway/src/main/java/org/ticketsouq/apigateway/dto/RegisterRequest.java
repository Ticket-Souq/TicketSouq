package org.ticketsouq.apigateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 3, max = 50) String name,
        @NotBlank @Size(min = 8) String password,
        @Size(min = 3, max = 50) String OrganizationName
) {}
