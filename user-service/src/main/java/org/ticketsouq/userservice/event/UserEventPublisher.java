package org.ticketsouq.userservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.USER_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.AUDIT_EVENT;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAuditEvent(AuditEvent event) {
        LogUtils.logEventPublished(USER_SERVICE,AUDIT_EVENT);
        kafkaTemplate.send(AUDIT_EVENT, event.madeById().toString(), event);
    }
}
