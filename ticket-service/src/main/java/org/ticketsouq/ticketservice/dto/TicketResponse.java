package org.ticketsouq.ticketservice.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record TicketResponse(
    UUID id,
    String ticketType,
    String eventTitle,
    Instant eventStartDate,
    Instant eventFinishDate,
    String eventPosterUrl,
    String eventStatus,
    BigDecimal price,
    String reservationStatus,
    boolean consumed,
    // Zone
    String zoneCategory,
    // Seat
    Integer row,
    Integer seatNumber,
    String seatCategory,
    LocalDateTime createdAt
) {}
