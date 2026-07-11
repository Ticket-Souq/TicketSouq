package org.ticketsouq.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.AccountGeneratedEvent;
import org.ticketsouq.notificationservice.service.NotificationService;

@Component
public class AccountGeneratedConsumer {

    private final NotificationService notificationService;

    public AccountGeneratedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "notification.account-generated")
    public void consume(AccountGeneratedEvent event) {
        notificationService.handleAccountGenerated(event);
    }
}
