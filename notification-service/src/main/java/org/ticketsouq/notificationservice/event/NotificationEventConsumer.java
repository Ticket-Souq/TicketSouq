package org.ticketsouq.notificationservice.event;


import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.service.NotificationService;
import org.ticketsouq.sharedmodule.ApiGateway.event.AccountsGeneratedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.EmailVerificationEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordChangedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordResetEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.ReservationCompletedEvent;
import org.ticketsouq.sharedmodule.UserService.events.OrganizationStatusChangedEvent;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;


    @KafkaListener(topics = PAYMENT_REFUNDED)
    public void refundCompletedConsumer(RefundCompletedEvent event) {
        notificationService.handleRefundCompleted(event);
    }


    @KafkaListener(topics = RESERVATION_COMPLETED)
    public void reservationCompletedConsumer(ReservationCompletedEvent event) {
        notificationService.handleReservationCompleted(event);
    }


    @KafkaListener(topics = USER_PASSWORD_RESET)
    public void passwordResetConsumer(PasswordResetEvent event) {
        notificationService.handlePasswordReset(event);
    }

    @KafkaListener(topics = USER_PASSWORD_CHANGE)
    public void passwordChangedConsumer(PasswordChangedEvent event) {
        notificationService.handlePasswordChanged(event);
    }

    @KafkaListener(topics = USER_EMAIL_VERIFICATION)
    public void emailVerificationConsumer(EmailVerificationEvent event) {
        notificationService.handleEmailVerification(event);
    }

    @KafkaListener(topics = ACCOUNTS_GENERATED)
    public void accountGeneratedConsumer(AccountsGeneratedEvent event) {
        notificationService.handleAccountGenerated(event);
    }

    @KafkaListener(topics = ORG_STATUS_CHANGED)
    public void orgStatusChangedConsumer(OrganizationStatusChangedEvent event) {
        notificationService.handleOrgStatusChanged(event);
    }
}
