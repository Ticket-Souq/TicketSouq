package org.ticketsouq.notificationservice.service;

import org.ticketsouq.notificationservice.dto.NotificationResponse;
import org.ticketsouq.notificationservice.dto.UnreadCountResponse;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    void handleEmailVerification(EmailVerificationEvent event);

    List<NotificationResponse> getNotifications(UUID userId);

    UnreadCountResponse getUnreadCount(UUID userId);

    void markAsRead(Long notificationId, UUID userId);

    void markAllAsRead(UUID userId);

}

