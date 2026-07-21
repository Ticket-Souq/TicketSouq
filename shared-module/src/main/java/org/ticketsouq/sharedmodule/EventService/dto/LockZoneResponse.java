package org.ticketsouq.sharedmodule.EventService.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LockZoneResponse(
    String status,
    LocalDateTime expiresAt,
    UUID zoneId,
    int quantity,
    BigDecimal totalPrice
) {}
