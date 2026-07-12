package org.ticketsouq.notificationservice.event;


import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.service.NotificationService;

@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;


    @KafkaListener(topics = "notification.refund-completed")
    public void RefundCompletedConsumer(RefundCompletedEvent event) {
        notificationService.handleRefundCompleted(event);
    }


    @KafkaListener(topics = "notification.payment-success")
    public void PaymentSuccessConsumer(PaymentSuccessEvent event) {
        notificationService.handlePaymentSuccess(event);
    }


    @KafkaListener(topics = "notification.password-reset")
    public void PasswordResetConsumer(PasswordResetEvent event) {
        System.out.println("start 4");
        notificationService.handlePasswordReset(event);
    }

    @KafkaListener(topics = "notification.password-changed")
    public void PasswordChangedConsumer(PasswordChangedEvent event) {
        notificationService.handlePasswordChanged(event);
    }

    @KafkaListener(topics = "notification.email-verification")
    public void EmailVerificationConsumer(EmailVerificationEvent event) {
        notificationService.handleEmailVerification(event);
    }

    @KafkaListener(topics = "notification.account-generated")
    public void AccountGeneratedConsumer(AccountGeneratedEvent event) {
        notificationService.handleAccountGenerated(event);
    }
}
