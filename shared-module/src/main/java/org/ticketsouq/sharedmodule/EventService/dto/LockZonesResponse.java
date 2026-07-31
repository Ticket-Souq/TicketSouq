package org.ticketsouq.sharedmodule.EventService.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LockZonesResponse(
    UUID reservationId,
    String status,
    LocalDateTime expiresAt,
    List<ZoneDetail> zones
) {
    public record ZoneDetail(UUID zoneId, Integer quantity) {}
}
