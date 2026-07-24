package org.ticketsouq.sharedmodule.ReservationService.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReservationCreatedEvent(
    UUID messageId,
    UUID reservationId,
    UUID customerId,
    UUID eventId,
    List<UUID> seatIds,
    UUID zoneId,
    Integer quantity,
    BigDecimal totalAmount
) {}
