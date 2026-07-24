package org.ticketsouq.reservationservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void publish(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .eventType(event.getClass().getName())
                .topic(topic)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event {} queued for topic {}", event.getClass().getName(), topic);
        } catch (DataIntegrityViolationException e) {
            log.debug("Outbox event {} already exists for aggregate {}, skipping", event.getClass().getName(), aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event {}: {}", event.getClass().getName(), e.getMessage());
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}
