package org.ticketsouq.analyticsservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.analyticsservice.service.impl.AnalyticsEventProcessingService;
import org.ticketsouq.sharedmodule.EventService.events.*;
import org.ticketsouq.sharedmodule.ReservationService.events.ReservationCompletedEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.ANALYTICS_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsEventConsumer {

    private final AnalyticsEventProcessingService processingService;

    @KafkaListener(topics = EVENT_CREATED)
    public void onEventCreated(EventCreatedEvent event) {
        LogUtils.logEventConsumed(ANALYTICS_SERVICE, EVENT_CREATED);
        processingService.handleEventCreated(event);
    }

    @KafkaListener(topics = EVENT_ACTIVATED)
    public void onEventActivated(EventActivatedEvent event) {
        LogUtils.logEventConsumed(ANALYTICS_SERVICE, EVENT_ACTIVATED);
        processingService.handleEventActivated(event);
    }

    @KafkaListener(topics = EVENT_COMPLETED)
    public void onEventCompleted(EventCompletedEvent event) {
        LogUtils.logEventConsumed(ANALYTICS_SERVICE, EVENT_COMPLETED);
        processingService.handleEventCompleted(event);
    }

    @KafkaListener(topics = EVENT_CANCELLED)
    public void onEventCancelled(EventCancelledEvent event) {
        LogUtils.logEventConsumed(ANALYTICS_SERVICE, EVENT_CANCELLED);
        processingService.handleEventCancelled(event);
    }

    @KafkaListener(topics = RESERVATION_COMPLETED)
    public void onReservationCompleted(ReservationCompletedEvent event) {
        LogUtils.logEventConsumed(ANALYTICS_SERVICE, RESERVATION_COMPLETED);
        processingService.handleReservationCompleted(event);
    }
}
