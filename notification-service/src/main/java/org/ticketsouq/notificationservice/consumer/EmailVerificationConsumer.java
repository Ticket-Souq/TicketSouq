package org.ticketsouq.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.service.NotificationService;

@Component
public class EmailVerificationConsumer {
    private final NotificationService notificationService;

    public EmailVerificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "notification.email-verification")
    public void consume(EmailVerificationEvent event) {
        notificationService.handleEmailVerification(event);
    }

}
