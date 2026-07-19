package org.ticketsouq.eventservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LockZoneResponse(
    String status,
    LocalDateTime expiresAt,
    UUID zoneId,
    Integer quantity
) {}
