package org.ticketsouq.paymentservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.paymentservice.service.PaymentService;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundRequestedEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.PAYMENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_REFUND_REQUEST;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = PAYMENT_REFUND_REQUEST)
    public void handleRefundRequest(RefundRequestedEvent event) {
        LogUtils.logEventConsumed(PAYMENT_SERVICE, PAYMENT_REFUND_REQUEST);
        paymentService.processRefundRequest(event.paymentId());
    }
}
