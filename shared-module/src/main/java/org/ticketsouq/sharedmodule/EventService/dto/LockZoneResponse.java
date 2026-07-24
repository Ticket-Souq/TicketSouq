package org.ticketsouq.sharedmodule.EventService.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.UUID;

public record LockZoneResponse(
    UUID reservationId,
    String status,
    LocalDateTime expiresAt,
    UUID zoneId,
    Integer quantity
) {}
