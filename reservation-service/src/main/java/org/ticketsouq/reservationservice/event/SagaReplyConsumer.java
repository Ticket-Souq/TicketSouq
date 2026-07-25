package org.ticketsouq.reservationservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.reservationservice.core.SagaOrchestrator;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmReplyEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentReplyEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketReplyEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.EVENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.RESERVATION_BEGIN;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyConsumer {

    private final SagaOrchestrator sagaOrchestrator;
    private final ReservationService reservationService;


    @KafkaListener(topics = RESERVATION_BEGIN)
    @Transactional
    public void handleBeginReservation(BeginReservationEvent event) {
        LogUtils.logEventConsumed(EVENT_SERVICE, RESERVATION_BEGIN);
        Reservation reservation = reservationService.createReservation(event);
        if (reservation == null) return;
        ReservationContext context = reservationService.createReservationContext(reservation, event);
        sagaOrchestrator.startSaga(context);
    }

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
