package org.ticketsouq.sharedmodule.EventService.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record LockZoneRequest(
    @NotNull UUID zoneId,
    @Positive @Max(value = 10, message = "A maximum of 10 tickets can be reserved") Integer quantity
) {
}
