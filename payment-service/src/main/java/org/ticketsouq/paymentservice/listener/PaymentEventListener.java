package org.ticketsouq.paymentservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentFailedEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_FAILED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_REFUNDED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_SUCCESS;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        kafkaTemplate.send(PAYMENT_SUCCESS, event.userId().toString(), event);
        log.info("Sent PaymentSuccessEvent to Kafka for userId={}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(PAYMENT_FAILED, event.userId().toString(), event);
        log.info("Sent PaymentFailedEvent to Kafka for userId={}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefundCompleted(RefundCompletedEvent event) {
        kafkaTemplate.send(PAYMENT_REFUNDED, event.userId().toString(), event);
        log.info("Sent RefundCompletedEvent to Kafka for userId={}", event.userId());
    }
}
