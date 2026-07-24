package org.ticketsouq.sharedmodule.TicketService.dto;

import jakarta.validation.constraints.NotNull;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;

import java.util.List;
import java.util.UUID;

public record CreateTicketRequest(
    @NotNull UUID reservationId,
    @NotNull UUID eventId,
    @NotNull UUID userId,
    @NotNull List<TicketReservationDto> tickets
) {}
