package org.ticketsouq.ticketservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OrganizerReserveRequest(
    @NotNull UUID eventId,
    @NotBlank String label,
    @NotBlank String holderName,
    BigDecimal price,
    String sectionName,
    UUID templateSeatId
) {}