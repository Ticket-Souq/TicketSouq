package org.ticketsouq.ticketservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateTicketsRequest(
    @NotNull UUID reservationId,
    @NotNull UUID userId,
    @NotNull UUID eventId,
    @NotNull String eventTitle,
    @NotNull Instant eventStartDate,
    @NotNull Instant eventFinishDate,
    @NotNull String eventPosterUrl,
    @NotNull String eventStatus,
    @NotNull @Size(min = 1) List<TicketItem> tickets
) {
    public record TicketItem(
        @NotNull String type,
        UUID seatId,
        Integer row,
        Integer seatNumber,
        UUID sectionId,
        String category,
        @NotNull BigDecimal price
    ) {}
}
