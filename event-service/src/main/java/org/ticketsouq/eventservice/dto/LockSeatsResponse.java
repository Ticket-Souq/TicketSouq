package org.ticketsouq.eventservice.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LockSeatsResponse(
    String status,
    LocalDateTime expiresAt,
    List<UUID> lockedSeats
) {}
