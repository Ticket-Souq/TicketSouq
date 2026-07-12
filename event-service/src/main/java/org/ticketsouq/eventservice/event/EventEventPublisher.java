package org.ticketsouq.eventservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventStatusChangedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventUpdatedEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEventCreated(EventCreatedEvent event) {
        LogUtils.log(EVENT_CREATED, event.eventId());
        kafkaTemplate.send(EVENT_CREATED, event.eventId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEventUpdated(EventUpdatedEvent event) {
        LogUtils.log(EVENT_UPDATED, event.eventId());
        kafkaTemplate.send(EVENT_UPDATED, event.eventId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEventCancelled(EventCancelledEvent event) {
        LogUtils.log(EVENT_CANCELLED, event.eventId());
        kafkaTemplate.send(EVENT_CANCELLED, event.eventId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEventStatusChanged(EventStatusChangedEvent event) {
        LogUtils.log(EVENT_STATUS_CHANGED, event.eventId());
        kafkaTemplate.send(EVENT_STATUS_CHANGED, event.eventId().toString(), event);
    }
}
