package org.ticketsouq.eventservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;
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
    public void sendEventCancelled(EventCancelledEvent event) {
        LogUtils.log(EVENT_CANCELLED, event.eventId());
        kafkaTemplate.send(EVENT_CANCELLED, event.eventId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAuditEvent(AuditEvent event) {
        LogUtils.log(AUDIT_EVENT, event.madeById());
        kafkaTemplate.send(AUDIT_EVENT, event.madeById().toString(), event);
    }

}
