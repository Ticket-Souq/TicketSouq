package org.ticketsouq.reservationservice.dto;

import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    UUID userId,
    UUID eventId,
    ReservationStatus status,
    Instant createdAt,
    Instant completedAt
) {}
