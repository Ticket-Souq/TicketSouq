package org.ticketsouq.ticketservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.ticketservice.service.EventSnapshotService;
import org.ticketsouq.sharedmodule.EventService.events.EventActivatedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCompletedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.EVENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.EVENT_ACTIVATED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.EVENT_CANCELLED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.EVENT_COMPLETED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.EVENT_CREATED;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSnapshotConsumer {

    private final EventSnapshotService eventSnapshotService;

    @KafkaListener(topics = EVENT_CREATED)
    public void handleEventCreated(EventCreatedEvent event) {
        LogUtils.logEventConsumed(EVENT_SERVICE, EVENT_CREATED);
        eventSnapshotService.applyCreatedEvent(event);
    }

    @KafkaListener(topics = EVENT_ACTIVATED)
    public void handleEventActivated(EventActivatedEvent event) {
        LogUtils.logEventConsumed(EVENT_SERVICE, EVENT_ACTIVATED);
        eventSnapshotService.applyActivatedEvent(event);
    }

    @KafkaListener(topics = EVENT_COMPLETED)
    public void handleEventCompleted(EventCompletedEvent event) {
        LogUtils.logEventConsumed(EVENT_SERVICE, EVENT_COMPLETED);
        eventSnapshotService.applyCompletedEvent(event);
    }

    @KafkaListener(topics = EVENT_CANCELLED)
    public void handleEventCancelled(EventCancelledEvent event) {
        LogUtils.logEventConsumed(EVENT_SERVICE, EVENT_CANCELLED);
        eventSnapshotService.applyCancelledEvent(event);
    }
}
