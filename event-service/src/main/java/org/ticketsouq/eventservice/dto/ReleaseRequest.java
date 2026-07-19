package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ReleaseRequest(
    @NotBlank String reservationId
) {}
