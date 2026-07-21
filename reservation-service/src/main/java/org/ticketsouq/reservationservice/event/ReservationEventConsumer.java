package org.ticketsouq.reservationservice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;
import org.ticketsouq.sharedmodule.ReservationService.events.ReservationCompleteEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.EVENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.RESERVATION_BEGIN;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.RESERVATION_COMPLETED;

@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final ReservationService reservationService;

    @KafkaListener(topics = RESERVATION_BEGIN)
    public void ReservationCompleteConsumer(BeginReservationEvent event) {
        LogUtils.logEventConsumed(EVENT_SERVICE, RESERVATION_COMPLETED);
        reservationService.createReservation(event);
    }
}
