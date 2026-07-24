package org.ticketsouq.reservationservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.reservationservice.core.SagaOrchestrator;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.EVENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.RESERVATION_BEGIN;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final ReservationService reservationService;
    private final SagaOrchestrator sagaOrchestrator;

    @KafkaListener(topics = RESERVATION_BEGIN)
    public void handleBeginReservation(BeginReservationEvent event) {
        MDC.put("reservationId", event.reservationId().toString());
        MDC.put("messageId", event.eventId().toString());
        MDC.put("kafkaTopic", RESERVATION_BEGIN);

        try {
        LogUtils.logEventConsumed(EVENT_SERVICE, RESERVATION_BEGIN);
        Reservation reservation = reservationService.createReservation(event);
        if (reservation == null) return;
        ReservationContext context = reservationService.createReservationContext(reservation, event);
        sagaOrchestrator.startSaga(context);
        } finally {
            MDC.clear();
        }
    }

}
