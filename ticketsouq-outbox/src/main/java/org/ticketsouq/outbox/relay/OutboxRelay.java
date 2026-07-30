package org.ticketsouq.outbox.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.outbox.entity.OutboxEvent;
import org.ticketsouq.outbox.entity.OutboxStatus;
import org.ticketsouq.outbox.repository.OutboxEventRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "#{@outboxProperties.pollInterval}")
    public void publishPendingEvents() {
        transactionTemplate.executeWithoutResult(status -> {
            repository.resetStuckInProgress(properties.getMaxRetries(),
                Instant.now().minus(Duration.ofMinutes(properties.getStaleMinutes())));
        });

        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEvent> pending = repository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING);
            for (OutboxEvent event : pending) {
                int claimed = repository.markInProgress(event.getId());
                if (claimed == 0) continue;

                try {
                    Object payload = deserialize(event);
                    kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload)
                        .get(10, TimeUnit.SECONDS);
                    markPublished(event.getId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    repository.findById(event.getId()).ifPresent(e2 -> {
                        e2.setStatus(OutboxStatus.PENDING);
                        repository.save(e2);
                    });
                    break;
                } catch (Exception e) {
                    handleFailure(event, e);
                }
            }
        });
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanPublishedEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        repository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, cutoff);
    }

    private Object deserialize(OutboxEvent event) {
        try {
            Class<?> eventClass = Class.forName(event.getEventType());
            return objectMapper.readValue(event.getPayload(), eventClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize outbox payload: " + event.getEventType(), e);
        }
    }

    private void markPublished(UUID id) {
        repository.findById(id).ifPresent(e -> {
            e.setStatus(OutboxStatus.PUBLISHED);
            e.setPublishedAt(Instant.now());
            repository.save(e);
        });
    }

    private void handleFailure(OutboxEvent event, Throwable ex) {
        repository.findById(event.getId()).ifPresent(e -> {
            e.setRetryCount(e.getRetryCount() + 1);
            if (e.getRetryCount() >= properties.getMaxRetries()) {
                e.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event {} failed after {} retries: {}", e.getId(), properties.getMaxRetries(), ex.getMessage());
            } else {
                e.setStatus(OutboxStatus.PENDING);
                log.warn("Outbox event {} publish failed (retry {}/{}): {}", e.getId(), e.getRetryCount(), properties.getMaxRetries(), ex.getMessage());
            }
            repository.save(e);
        });
    }
}
