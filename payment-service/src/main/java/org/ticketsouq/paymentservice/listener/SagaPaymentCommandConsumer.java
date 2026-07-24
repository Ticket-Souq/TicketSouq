package org.ticketsouq.paymentservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentReplyEvent;

import java.math.BigDecimal;
import java.util.UUID;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_PAYMENT_COMMAND;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_PAYMENT_REPLY;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaPaymentCommandConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = SAGA_PAYMENT_COMMAND)
    @Transactional
    public void handleSagaPaymentCommand(SagaPaymentCommand command) {
        log.info("Received SagaPaymentCommand for reservationId={}", command.reservationId());

        PaymentModel existing = paymentRepository.findByReservationID(command.reservationId())
            .orElse(null);

        if (existing != null) {
            log.info("Payment already exists for reservationId={}, paymentId={}, status={}",
                command.reservationId(), existing.getId(), existing.getPaymentStatus());
            sendReply(command.reservationId(), existing.getId(),
                existing.getPaymentStatus() == org.ticketsouq.paymentservice.enums.PaymentStatus.SUCCESS,
                existing.getPaymentStatus() == org.ticketsouq.paymentservice.enums.PaymentStatus.SUCCESS ? null : "Payment failed");
            return;
        }

        var request = new org.ticketsouq.paymentservice.dto.PaymentRequest(
            command.reservationId(),
            command.userId(),
            command.eventId(),
            command.amount(),
            "USD"
        );

        var response = paymentProvider.pay(request);

        PaymentModel payment = PaymentModel.builder()
            .id(response.paymentID())
            .reservationID(command.reservationId())
            .customerID(command.userId())
            .amount(command.amount())
            .currency("USD")
            .paymentStatus(response.paymentStatus())
            .build();

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created the payment first
            log.warn("Race condition detected for reservationId={}, fetching existing payment", command.reservationId());
            PaymentModel existingPayment = paymentRepository.findByReservationID(command.reservationId())
                .orElseThrow(() -> new IllegalStateException("Payment was created but not found"));
            sendReply(command.reservationId(), existingPayment.getId(),
                existingPayment.getPaymentStatus() == org.ticketsouq.paymentservice.enums.PaymentStatus.SUCCESS,
                existingPayment.getPaymentStatus() == org.ticketsouq.paymentservice.enums.PaymentStatus.SUCCESS ? null : "Payment failed");
            return;
        }

        sendReply(command.reservationId(), response.paymentID(),
            response.paymentStatus() == org.ticketsouq.paymentservice.enums.PaymentStatus.SUCCESS,
            response.paymentStatus() == org.ticketsouq.paymentservice.enums.PaymentStatus.SUCCESS ? null : response.msg());
        log.info("Published SagaPaymentReplyEvent for reservationId={}, success={}", command.reservationId(), 
            response.paymentStatus() == org.ticketsouq.paymentservice.enums.PaymentStatus.SUCCESS);
    }

    private void sendReply(UUID reservationId, UUID paymentId, boolean success, String failReason) {
        SagaPaymentReplyEvent reply = new SagaPaymentReplyEvent(reservationId, paymentId, success, failReason);
        kafkaTemplate.send(SAGA_PAYMENT_REPLY, reservationId.toString(), reply);
    }
}