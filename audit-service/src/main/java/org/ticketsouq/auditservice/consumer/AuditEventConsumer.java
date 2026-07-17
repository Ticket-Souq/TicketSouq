package org.ticketsouq.auditservice.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.auditservice.entity.AuditLog;
import org.ticketsouq.auditservice.repository.AuditLogRepository;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.AUDIT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.AUDIT_EVENT;

@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditLogRepository repository;

    @KafkaListener(topics = AUDIT_EVENT)
    public void consume(AuditEvent event) {
        LogUtils.logEventConsumed(AUDIT_SERVICE, AUDIT_EVENT);
        AuditLog entity = new AuditLog(null ,event.action(), event.madeById(), event.reason(), event.madeAt());
        repository.save(entity);
    }
}
