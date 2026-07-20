package org.ticketsouq.reservationservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReservationRequest(
    @NotNull UUID eventId,
    List<UUID> seatIds,
    UUID zoneId,
    Integer quantity,
    @NotNull @DecimalMin(value = "0.01") BigDecimal totalAmount
) {}
