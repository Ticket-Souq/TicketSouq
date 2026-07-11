package org.ticketsouq.notificationservice.service;

import org.ticketsouq.notificationservice.dto.NotificationResponse;
import org.ticketsouq.notificationservice.dto.UnreadCountResponse;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.event.PasswordChangedEvent;
import org.ticketsouq.notificationservice.event.PasswordResetEvent;
import org.ticketsouq.notificationservice.event.PaymentSuccessEvent;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    void handleEmailVerification(EmailVerificationEvent event);

    List<NotificationResponse> getNotifications(UUID userId);

    UnreadCountResponse getUnreadCount(UUID userId);

    void markAsRead(Long notificationId, UUID userId);

    void markAllAsRead(UUID userId);

    void handlePasswordReset(PasswordResetEvent event);
    void handlePasswordChanged(PasswordChangedEvent event);
    void handlePaymentSuccess(PaymentSuccessEvent event);
}

