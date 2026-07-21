package org.ticketsouq.reservationservice.dto;

import org.ticketsouq.reservationservice.enums.ReservationStatus;
import org.ticketsouq.sharedmodule.EventService.dto.LockItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    UUID customerId,
    UUID eventId,
    ReservationStatus status,
    LocalDateTime expiresAt,
    List<LockItem> items,
    BigDecimal totalAmount,
    String failureReason,
    Instant createdAt
) {}
