package org.ticketsouq.eventservice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;
import org.ticketsouq.sharedmodule.ReservationService.events.ReservationCompleteEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.EVENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.RESERVATION_COMPLETED;

@Component
@RequiredArgsConstructor
public class EventEventConsumer {

    private final LockService lockService;

    @KafkaListener(topics = RESERVATION_COMPLETED)
    public void ReservationCompleteConsumer(ReservationCompleteEvent event) {
        LogUtils.logEventConsumed(EVENT_SERVICE, RESERVATION_COMPLETED);
        if (event.reservationStatus().equals(ReservationStatus.COMPLETED)) {
            lockService.confirm(String.valueOf(event.reservationId()));
        } else {
            lockService.release(String.valueOf(event.reservationId()));
        }
    }
}
