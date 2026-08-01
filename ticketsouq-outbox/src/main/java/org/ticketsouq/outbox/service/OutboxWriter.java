package org.ticketsouq.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.outbox.entity.OutboxEvent;
import org.ticketsouq.outbox.entity.OutboxStatus;
import org.ticketsouq.outbox.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(Object event, String topic, String aggregateId) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .eventType(event.getClass().getName())
                .topic(topic)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
            repository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to persist outbox event {}: {}", event.getClass().getName(), e.getMessage());
            throw new RuntimeException("Failed to persist outbox event", e);
        }
    }
}
