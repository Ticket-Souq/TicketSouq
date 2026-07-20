package org.ticketsouq.sharedmodule.EventService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record LockZoneRequest(
    @NotBlank String reservationId,
    @NotNull UUID zoneId,
    @Positive Integer quantity
) {}
