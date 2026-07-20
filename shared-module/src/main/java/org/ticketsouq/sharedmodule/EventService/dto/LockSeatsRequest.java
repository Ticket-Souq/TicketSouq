package org.ticketsouq.sharedmodule.EventService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record LockSeatsRequest(
    @NotBlank String reservationId,
    @NotEmpty List<UUID> seatIds
) {}
