package org.ticketsouq.notificationservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationTemplate {

    REGISTRATION(
        NotificationType.REGISTRATION,
        "Welcome to Ticketaty",
        "Your account has been created successfully.",
        "Verify your Ticketaty account",
        "email/registration"
    ),

    PASSWORD_RESET(
        NotificationType.PASSWORD_RESET,
        "Password Reset",
        "Your password reset request has been received.",
        "Reset your password",
        "email/password-reset"
    ),

    PASSWORD_CHANGED(
        NotificationType.PASSWORD_CHANGED,
        "Password Changed",
        "Your password has been changed successfully.",
        "Your password has been changed",
        "email/password-changed"
    ),

    PAYMENT_SUCCESS(
        NotificationType.PAYMENT_SUCCESS,
        "Payment Successful",
        "Your payment has been completed successfully.",
        "Payment Successful",
        "email/payment-success"
    ),

    EVENT_CANCELLED(
        NotificationType.EVENT_CANCELLED,
        "Event Cancelled",
        "Unfortunately, your event has been cancelled. Your payment has been refunded.",
        "Event Cancelled",
        "email/refund-completed"
    ),
    ACCOUNT_GENERATED(
        NotificationType.ACCOUNT_GENERATED,
        null,
        null,
        "Your Ticketaty Account",
        "email/account-generated"
    )
    ;


    private final NotificationType notificationType;
    private final String inAppTitle;
    private final String inAppMessage;
    private final String emailSubject;
    private final String emailTemplate;
}
