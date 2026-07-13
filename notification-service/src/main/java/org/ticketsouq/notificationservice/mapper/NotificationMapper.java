package org.ticketsouq.notificationservice.mapper;

import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.dto.NotificationResponse;
import org.ticketsouq.notificationservice.dto.UnreadCountResponse;
import org.ticketsouq.notificationservice.entity.Notification;
import org.ticketsouq.notificationservice.enums.NotificationType;

import java.util.UUID;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getType(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }

    public Notification create(
        UUID userId,
        String title,
        String message,
        NotificationType type
    ) {
        return Notification.builder()
            .userId(userId)
            .title(title)
            .message(message)
            .type(type)
            .build();
    }
    public UnreadCountResponse toUnreadCountResponse(long count) {
        return new UnreadCountResponse(count);
    }
}
