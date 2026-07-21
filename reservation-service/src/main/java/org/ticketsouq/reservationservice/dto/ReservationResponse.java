package org.ticketsouq.reservationservice.dto;


import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    UUID customerId,
    UUID eventId,
    List<UUID> seatIds,
    UUID zoneId,
    Integer quantity,
    BigDecimal totalAmount,
    ReservationStatus status,
    UUID paymentId,
    UUID ticketId,
    Instant createdAt
) {}
