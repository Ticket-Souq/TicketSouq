package org.ticketsouq.paymentservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentCompensateCommand;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaPaymentCompensateConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    @KafkaListener(topics = TOPIC_NAMES.SAGA_PAYMENT_COMPENSATE, groupId = "payment-service")
    @Transactional
    public void handleSagaPaymentCompensate(SagaPaymentCompensateCommand command) {
        log.info("Received SagaPaymentCompensateCommand for paymentId={}, reservationId={}", 
            command.paymentId(), command.reservationId());

        PaymentModel payment = paymentRepository.findById(command.paymentId()).orElse(null);
        if (payment == null) {
            log.warn("Payment not found for paymentId={}, skipping compensation", command.paymentId());
            return;
        }

        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment {} already refunded, skipping", command.paymentId());
            return;
        }

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            paymentProvider.refund(command.paymentId());
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Refund processed for paymentId={}", command.paymentId());
        } else {
            log.info("Payment {} not in SUCCESS status ({}), skipping refund", 
                command.paymentId(), payment.getPaymentStatus());
        }
    }
}