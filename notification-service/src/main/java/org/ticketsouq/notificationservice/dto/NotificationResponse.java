package org.ticketsouq.notificationservice.dto;

import lombok.Getter;
import org.ticketsouq.notificationservice.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String title,
    String message,
    NotificationType type,
    boolean isRead,
    LocalDateTime createdAt
) {
}
