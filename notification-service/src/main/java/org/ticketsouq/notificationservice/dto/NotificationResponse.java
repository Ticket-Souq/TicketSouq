package org.ticketsouq.notificationservice.dto;

import org.ticketsouq.notificationservice.enums.NotificationType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String title,
    String message,
    NotificationType type,
    boolean isRead,
    Instant createdAt
) {
}
