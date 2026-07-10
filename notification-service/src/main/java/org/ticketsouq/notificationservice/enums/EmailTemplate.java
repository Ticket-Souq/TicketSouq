package org.ticketsouq.notificationservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailTemplate {

    REGISTRATION(
        "Verify your Ticketaty account",
        "email/registration"
    ),

    PASSWORD_RESET(
        "Reset your password",
        "email/password-reset"
    ),

    PAYMENT_SUCCESS(
        "Payment Successful",
        "email/payment-success"
    ),

    EVENT_CANCELLED(
        "Event Cancelled",
        "email/event-cancelled"
    );

    private final String subject;
    private final String template;
}
