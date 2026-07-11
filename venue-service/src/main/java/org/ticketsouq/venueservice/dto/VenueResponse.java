package org.ticketsouq.venueservice.dto;

import org.ticketsouq.venueservice.model.Type;

import java.util.UUID;

public record VenueResponse(
        UUID id,
        UUID orgId,
        String name,
        String address,
        Type type
) {}
