package org.ticketsouq.eventservice.dto;

import lombok.Builder;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.util.UUID;

@Builder
public record EventResponse(

    UUID id,

    String title,

    String description,

    UUID categoryId,

    String categoryName,

    String posterUrl,

    EventStatus status,

    BookingModel bookingModel,

    Instant startDate,

    Instant finishDate

) {}
