package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventRequest(
    @NotNull @NotEmpty @NotBlank String title,
    @NotNull @NotEmpty @NotBlank String description,
    @NotNull @NotEmpty @NotBlank String location,
    UUID venueTemplateId,
    String eventCategoryName,
    BookingModel bookingModel,
    Instant startDate,
    Instant finishDate,
    List<CreateSectionRequest> sections
) {
    public record CreateSectionRequest(UUID id, String name, Integer capacity, String color, BigDecimal price,
                             List<CreateSeatRequest> seats) implements Serializable {
        public record CreateSeatRequest(UUID id, String lable, SeatStatus status) implements Serializable {
        }
    }
}
