package org.ticketsouq.notificationservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventDetailsResponse(
    UUID id,
    String name,
    String location,
    LocalDateTime startDate
) {
}
