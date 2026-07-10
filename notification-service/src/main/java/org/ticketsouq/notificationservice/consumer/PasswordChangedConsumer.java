package org.ticketsouq.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.PasswordChangedEvent;
import org.ticketsouq.notificationservice.service.NotificationService;

@Component
public class PasswordChangedConsumer {
    private final NotificationService notificationService;

    public PasswordChangedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "notification.password-changed")
    public void handlePasswordChanged(PasswordChangedEvent event) {
        notificationService.handlePasswordChanged(event);
    }
}
