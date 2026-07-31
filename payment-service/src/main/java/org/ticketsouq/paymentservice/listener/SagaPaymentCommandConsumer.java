package org.ticketsouq.paymentservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.outbox.service.OutboxWriter;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
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
    private final OutboxWriter outboxWriter;

    @KafkaListener(topics = SAGA_PAYMENT_COMMAND)
    @Transactional
    public void handleSagaPaymentCommand(SagaPaymentCommand command) {
        log.info("Received SagaPaymentCommand for reservationId={}", command.reservationId());

        PaymentModel existing = paymentRepository.findByReservationID(command.reservationId())
            .orElse(null);

        if (existing != null) {
            log.info("Payment already exists for reservationId={}, paymentId={}, status={}",
                command.reservationId(), existing.getId(), existing.getPaymentStatus());
            if (existing.getPaymentStatus() == PaymentStatus.PENDING) {
                log.info("Payment is still pending for reservationId={}, waiting for webhook completion", command.reservationId());
                return;
            }
            sendReply(
                command.reservationId(),
                existing.getId(),
                existing.getPaymentStatus() == PaymentStatus.SUCCESS,
                existing.getPaymentStatus() == PaymentStatus.SUCCESS ? null : "Payment failed"
            );
            return;
        }

        var request = new PaymentRequest(command.reservationId(), command.userId(), command.eventId(), command.amount());

        try {
            var response = paymentProvider.pay(request);

            if (response.paymentStatus() == PaymentStatus.PENDING) {
                log.info("Payment initiated for reservationId={}, paymentId={} and waiting for async completion",
                    command.reservationId(), response.paymentID());
                return;
            }

            sendReply(
                command.reservationId(),
                response.paymentID(),
                response.paymentStatus() == PaymentStatus.SUCCESS,
                response.paymentStatus() == PaymentStatus.SUCCESS ? null : response.msg()
            );
            log.info("Published SagaPaymentReplyEvent for reservationId={}, success={}", command.reservationId(),
                response.paymentStatus() == PaymentStatus.SUCCESS);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for reservationId={}, fetching existing payment", command.reservationId());
            PaymentModel existingPayment = paymentRepository.findByReservationID(command.reservationId())
                .orElseThrow(() -> new IllegalStateException("Payment was created but not found"));
            if (existingPayment.getPaymentStatus() == PaymentStatus.PENDING) {
                log.info("Existing payment is pending for reservationId={}, waiting for webhook completion", command.reservationId());
                return;
            }
            sendReply(
                command.reservationId(),
                existingPayment.getId(),
                existingPayment.getPaymentStatus() == PaymentStatus.SUCCESS,
                existingPayment.getPaymentStatus() == PaymentStatus.SUCCESS ? null : "Payment failed"
            );
        } catch (RuntimeException e) {
            log.error("Failed to process SagaPaymentCommand for reservationId={}: {}", command.reservationId(), e.getMessage(), e);
            sendReply(command.reservationId(), null, false, e.getMessage());
        }
    }

    private void sendReply(UUID reservationId, UUID paymentId, boolean success, String failReason) {
        SagaPaymentReplyEvent reply = new SagaPaymentReplyEvent(reservationId, paymentId, success, failReason);
        outboxWriter.save(reply, SAGA_PAYMENT_REPLY, reservationId.toString());
    }
}
