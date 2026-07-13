package org.ticketsouq.eventservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.ticketsouq.eventservice.Mapper.EventFrontendDto;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventWithLayoutRequest(
        @NotBlank String title,
        String description,
        @NotNull UUID venueId,
        @NotNull UUID organizationId,
        UUID createdBy,
        String posterUrl,
        @NotNull EventStatus status,
        @NotNull BookingModel bookingModel,
        @NotNull Instant startDate,
        @NotNull Instant finishDate,
        @Valid @NotNull List<EventFrontendDto.RowDto> rows,
        EventFrontendDto.StageDto stage,
        @Valid @NotNull List<EventFrontendDto.CategoryDto> categories,
        List<EventFrontendDto.VerticalAisleDto> verticalAisles
) {}
