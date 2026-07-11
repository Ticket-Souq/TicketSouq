package org.ticketsouq.venueservice.dto;

import org.ticketsouq.sharedmodule.Validation.NullOrNotBlank;
import org.ticketsouq.venueservice.model.Type;

public record UpdateVenueRequest(
        @NullOrNotBlank String name,
        @NullOrNotBlank String address,
        Type type
) {}
