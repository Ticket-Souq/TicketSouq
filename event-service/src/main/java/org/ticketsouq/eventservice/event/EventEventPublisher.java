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

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.EVENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEventCreated(EventCreatedEvent event) {
        LogUtils.logEventPublished(EVENT_SERVICE ,EVENT_CREATED);
        kafkaTemplate.send(EVENT_CREATED, event.eventId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEventCancelled(EventCancelledEvent event) {
        LogUtils.logEventPublished(EVENT_SERVICE ,EVENT_CANCELLED);
        kafkaTemplate.send(EVENT_CANCELLED, event.eventId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAuditEvent(AuditEvent event) {
        LogUtils.logEventPublished(EVENT_SERVICE ,AUDIT_EVENT);
        kafkaTemplate.send(AUDIT_EVENT, event.madeById().toString(), event);
    }

}
