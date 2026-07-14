package org.ticketsouq.auditservice.consumer;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.auditservice.entity.AuditLog;
import org.ticketsouq.auditservice.repository.AuditLogRepository;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;

@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditLogRepository repository;

    @KafkaListener(topics = "audit.event", groupId = "${spring.application.name}")
    public void consume(AuditEvent event) {
        log.info("Received audit event: action={}, madeById={}", event.action(), event.madeById());

        var entity = AuditLog.create(event.action(), event.madeById(), event.reason(), event.madeAt());

        repository.save(entity);
    }
}
