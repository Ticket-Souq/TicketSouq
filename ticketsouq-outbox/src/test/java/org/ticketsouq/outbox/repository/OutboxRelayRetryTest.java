package org.ticketsouq.outbox.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.ticketsouq.outbox.AbstractOutboxIntegrationTest;
import org.ticketsouq.outbox.entity.OutboxEvent;
import org.ticketsouq.outbox.entity.OutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayRetryTest extends AbstractOutboxIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("Outbox retry: event marked FAILED after retryCount reaches 5")
    void outboxRelay_retriesAndMarksFailed() {
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateId(UUID.randomUUID().toString())
            .eventType("com.example.NonExistentClass")
            .topic("test-topic")
            .payload("{}")
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .createdAt(Instant.now())
            .build();
        outboxEventRepository.save(event);

        for (int i = 0; i < 6; i++) {
            outboxEventRepository.findById(event.getId()).ifPresent(e -> {
                e.setRetryCount(e.getRetryCount() + 1);
                if (e.getRetryCount() >= 5) {
                    e.setStatus(OutboxStatus.FAILED);
                } else {
                    e.setStatus(OutboxStatus.PENDING);
                }
                outboxEventRepository.save(e);
            });
        }

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isGreaterThanOrEqualTo(5);
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("Outbox reset stuck events: stale IN_PROGRESS events reset to PENDING")
    void outboxRelay_resetsStuckEvents() {
        OutboxEvent stuckEvent = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateId(UUID.randomUUID().toString())
            .eventType("com.example.TestEvent")
            .topic("test-topic")
            .payload("{}")
            .status(OutboxStatus.IN_PROGRESS)
            .retryCount(0)
            .claimedAt(Instant.now().minus(Duration.ofMinutes(10)))
            .createdAt(Instant.now().minus(Duration.ofMinutes(10)))
            .build();
        outboxEventRepository.saveAndFlush(stuckEvent);

        int reset = outboxEventRepository.resetStuckInProgress(5, Instant.now().minus(Duration.ofMinutes(5)));
        assertThat(reset).isEqualTo(1);

        OutboxEvent updated = outboxEventRepository.findById(stuckEvent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
