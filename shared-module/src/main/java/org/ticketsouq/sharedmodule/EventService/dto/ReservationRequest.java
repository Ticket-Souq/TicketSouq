package org.ticketsouq.sharedmodule.EventService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationRequest(
    @NotNull UUID eventId,
    @NotBlank String reservationId
) {}
