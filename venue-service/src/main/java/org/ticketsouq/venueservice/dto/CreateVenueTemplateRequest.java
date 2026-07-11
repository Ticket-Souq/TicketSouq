package org.ticketsouq.venueservice.dto;

import jakarta.validation.constraints.NotNull;

public record CreateVenueTemplateRequest(
        @NotNull String layout
) {}
