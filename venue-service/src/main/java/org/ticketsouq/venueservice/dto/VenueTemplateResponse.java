package org.ticketsouq.venueservice.dto;

import java.util.UUID;

public record VenueTemplateResponse(
        UUID id,
        String layout
) {}
