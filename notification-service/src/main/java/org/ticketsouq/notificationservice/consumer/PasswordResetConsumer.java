package org.ticketsouq.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.PasswordResetEvent;
import org.ticketsouq.notificationservice.service.NotificationService;

@Component
public class PasswordResetConsumer {
    private final NotificationService notificationService;

    public PasswordResetConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "notification.password-reset")
    public void handlePasswordReset(PasswordResetEvent event) {
        System.out.println("start 4");
        notificationService.handlePasswordReset(event);
    }
}

