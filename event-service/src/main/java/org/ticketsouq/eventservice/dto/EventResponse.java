package org.ticketsouq.eventservice.dto;

import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String title,
        String description,
        UUID venueId,
        UUID organizationId,
        UUID createdBy,
        String posterUrl,
        EventStatus status,
        BookingModel bookingModel,
        Instant startDate,
        Instant finishDate
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getVenueId(),
                event.getOrganizationId(),
                event.getCreatedBy(),
                event.getPosterUrl(),
                event.getStatus(),
                event.getBookingModel(),
                event.getStartDate(),
                event.getFinishDate()
        );
    }
}
