package org.ticketsouq.sharedmodule.ReservationService.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(
    @NotBlank String reservationId
) {}
