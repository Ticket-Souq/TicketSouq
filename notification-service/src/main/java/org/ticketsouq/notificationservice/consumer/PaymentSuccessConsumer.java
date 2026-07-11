package org.ticketsouq.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.PaymentSuccessEvent;
import org.ticketsouq.notificationservice.service.NotificationService;

@Component
public class PaymentSuccessConsumer {

    private final NotificationService notificationService;

    public PaymentSuccessConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "notification.payment-success")
    public void consume(PaymentSuccessEvent event) {
        notificationService.handlePaymentSuccess(event);
    }
}
