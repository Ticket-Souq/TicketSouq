package org.ticketsouq.sharedmodule.ReservationService.dto;

import jakarta.validation.constraints.NotBlank;

public record ReleaseRequest(
    @NotBlank String reservationId
) {}
