package org.ticketsouq.eventservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank String title,
        String description,
        UUID venueId,
        @NotNull UUID organizationId,
        UUID createdBy,
        String posterUrl,
        @NotNull EventStatus status,
        @NotNull BookingModel bookingModel,
        @NotNull Instant startDate,
        @NotNull Instant finishDate,
        @Valid List<SectionWithSeats> sections
) {
    public record SectionWithSeats(
            @NotBlank String name,
            Integer capacity,
            String color,
            BigDecimal price,
            @Valid List<SeatInSection> seats
    ) {}

    public record SeatInSection(
            @NotNull Integer row,
            @NotNull Integer col,
            @NotBlank String lable,
            BigDecimal price
    ) {}
}
