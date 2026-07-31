package org.ticketsouq.sharedmodule.EventService.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record LockZonesRequest(
    @NotEmpty @Valid List<ZoneItem> zones
) {
    public record ZoneItem(@NotNull UUID zoneId, @Positive Integer quantity) {}
}
