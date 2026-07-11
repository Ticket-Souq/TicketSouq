package org.ticketsouq.venueservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.ticketsouq.venueservice.model.Type;

import java.util.UUID;

public record CreateVenueRequest(
        @NotNull UUID orgId,
        @NotBlank String name,
        @NotBlank String address,
        @NotNull Type type
) {}
