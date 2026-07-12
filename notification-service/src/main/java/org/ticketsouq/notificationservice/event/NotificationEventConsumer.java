package org.ticketsouq.notificationservice.event;


import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.service.NotificationService;
import org.ticketsouq.sharedmodule.ApiGateway.event.AccountsGeneratedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.EmailVerificationEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordChangedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordResetEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;


    @KafkaListener(topics = PAYMENT_REFUNDED)
    public void RefundCompletedConsumer(RefundCompletedEvent event) {
        notificationService.handleRefundCompleted(event);
    }


    @KafkaListener(topics = PAYMENT_SUCCESS)
    public void PaymentSuccessConsumer(PaymentSuccessEvent event) {
        notificationService.handlePaymentSuccess(event);
    }


    @KafkaListener(topics = USER_PASSWORD_RESET)
    public void PasswordResetConsumer(PasswordResetEvent event) {
        notificationService.handlePasswordReset(event);
    }

    @KafkaListener(topics = USER_PASSWORD_CHANGE)
    public void PasswordChangedConsumer(PasswordChangedEvent event) {
        notificationService.handlePasswordChanged(event);
    }

    @KafkaListener(topics = USER_EMAIL_VERIFICATION)
    public void EmailVerificationConsumer(EmailVerificationEvent event) {
        notificationService.handleEmailVerification(event);
    }

    @KafkaListener(topics = ACCOUNTS_GENERATED)
    public void AccountGeneratedConsumer(AccountsGeneratedEvent event) {
        notificationService.handleAccountGenerated(event);
    }
}
