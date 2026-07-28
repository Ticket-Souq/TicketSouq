package org.ticketsouq.eventservice.dto;

import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventFullResponse (
    UUID id, String title, String description, String location, UUID venueTemplateId,
    String eventCategoryName, String organization, String PosterUrl, EventStatus status,
    BookingModel bookingModel, Instant startDate, Instant finishDate,
    List<SectionFullResponse> sections){
        public record SectionFullResponse(UUID id, UUID templateSectionId, String name, Integer capacity, Integer remainingCapacity, String color,
                                 BigDecimal price, List<SeatFullResponse> seats) {

            public record SeatFullResponse(UUID id, UUID templateSeatId, SeatStatus status) {}
        }
}
