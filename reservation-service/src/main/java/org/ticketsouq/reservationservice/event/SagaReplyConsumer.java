package org.ticketsouq.reservationservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.reservationservice.core.SagaOrchestrator;
import org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmReplyEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentReplyEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketReplyEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyConsumer {

    private final SagaOrchestrator sagaOrchestrator;


    @KafkaListener(topics = TOPIC_NAMES.SAGA_PAYMENT_REPLY)
    public void handlePaymentReply(SagaPaymentReplyEvent event) {
        log.debug("Received SagaPaymentReplyEvent for reservationId={}, success={}", event.reservationId(), event.success());
        try {
            sagaOrchestrator.handlePaymentReply(event);
        } catch (Exception e) {
            log.error("Failed to handle payment reply for reservationId={}: {}", event.reservationId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = TOPIC_NAMES.SAGA_TICKET_REPLY)
    public void handleTicketReply(SagaTicketReplyEvent event) {
        log.debug("Received SagaTicketReplyEvent for reservationId={}, success={}", event.reservationId(), event.success());
        try {
            sagaOrchestrator.handleTicketReply(event);
        } catch (Exception e) {
            log.error("Failed to handle ticket reply for reservationId={}: {}", event.reservationId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = TOPIC_NAMES.SAGA_LOCK_CONFIRM_REPLY)
    public void handleLockConfirmReply(SagaLockConfirmReplyEvent event) {
        log.debug("Received SagaLockConfirmReplyEvent for reservationId={}, success={}", event.reservationId(), event.success());
        try {
            sagaOrchestrator.handleLockConfirmReply(event);
        } catch (Exception e) {
            log.error("Failed to handle lock confirm reply for reservationId={}: {}", event.reservationId(), e.getMessage(), e);
            throw e;
        }
    }
}
