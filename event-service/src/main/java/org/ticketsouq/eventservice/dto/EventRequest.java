package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record EventRequest(
        @NotBlank String title,
        String description,
        UUID venueId,
        UUID organizationId,
        UUID createdBy,
        String posterUrl,
        @NotNull EventStatus status,
        @NotNull BookingModel bookingModel,
        @NotNull Instant startDate,
        @NotNull Instant finishDate
) {}



/////////////////////////////////

//
//public record UpdateEventRequest(
//    UUID eventId,
//    list<updateSectionRequest> sections
//) {}
//
//public record updateSectionRequest(
//    UUID sectionId,
//    int price,
//    list<updateSeatRequest> seats --> null
//) {}
//
//public record updateSeatRequest(
//    UUID seatid,
//    int price
//) {}
