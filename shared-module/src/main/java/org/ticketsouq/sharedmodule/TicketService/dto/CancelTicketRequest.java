package org.ticketsouq.sharedmodule.TicketService.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CancelTicketRequest(
    @NotNull UUID reservationId
) {}
