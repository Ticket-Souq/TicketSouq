package org.ticketsouq.ticketservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTicketStatusRequest(
    @NotBlank String reservationStatus
) {}
