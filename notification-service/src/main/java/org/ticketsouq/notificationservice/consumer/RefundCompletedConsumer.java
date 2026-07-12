package org.ticketsouq.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.RefundCompletedEvent;
import org.ticketsouq.notificationservice.service.NotificationService;

@Component
public class RefundCompletedConsumer {
    private final NotificationService notificationService;

    public RefundCompletedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;

    }
    @KafkaListener(topics = "notification.refund-completed")
    public void consume(RefundCompletedEvent event) {
        notificationService.handleRefundCompleted(event);
    }
}
