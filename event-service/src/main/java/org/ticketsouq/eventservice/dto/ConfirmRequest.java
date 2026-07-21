package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(
    @NotBlank String reservationId
) {}
