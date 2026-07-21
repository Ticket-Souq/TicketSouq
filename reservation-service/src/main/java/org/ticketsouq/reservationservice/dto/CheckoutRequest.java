package org.ticketsouq.reservationservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
    @NotNull UUID eventId,
    List<UUID> seatIds,
    UUID zoneId,
    Integer quantity
) {}
