package org.ticketsouq.sharedmodule.EventService.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LockSeatsResponse(
    UUID reservationId,
    String status,
    LocalDateTime expiresAt,
    List<UUID> lockedSeats
) {}
