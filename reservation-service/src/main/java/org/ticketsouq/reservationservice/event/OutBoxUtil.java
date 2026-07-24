package org.ticketsouq.reservationservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;

import java.time.Duration;
import java.time.Instant;


@RequiredArgsConstructor
@Component
@Slf4j
public class OutBoxUtil {
    private final OutboxEventRepository outboxEventRepository;
    public static final int MAX_RETRIES = 5;
    public static final int STALE_IN_PROGRESS_MINUTES = 5;
    @Transactional
    public void handlePublishSuccess(OutboxEvent event) {
        outboxEventRepository.findById(event.getId()).ifPresent(e -> {
            e.setStatus(OutboxStatus.PUBLISHED);
            e.setPublishedAt(Instant.now());
            outboxEventRepository.save(e);
        });
    }

    @Transactional
    public void handlePublishFailure(OutboxEvent event, Throwable ex) {
        outboxEventRepository.findById(event.getId()).ifPresent(e -> {
            e.setRetryCount(e.getRetryCount() + 1);
            if (e.getRetryCount() >= MAX_RETRIES) {
                e.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event {} failed after {} retries for topic {}: {}",
                    e.getId(), MAX_RETRIES, e.getTopic(), ex.getMessage());
            } else {
                e.setStatus(OutboxStatus.PENDING);
                log.warn("Outbox event {} publish failed (retry {}/{}): {}",
                    e.getId(), e.getRetryCount(), MAX_RETRIES, ex.getMessage());
            }
            outboxEventRepository.save(e);
        });
    }
    @Transactional
    public void resetStuckEvents() {
        Instant staleThreshold = Instant.now().minus(Duration.ofMinutes(STALE_IN_PROGRESS_MINUTES));
        int reset = outboxEventRepository.resetStuckInProgress(MAX_RETRIES, staleThreshold);
        if (reset > 0) {
            log.warn("Reset {} stuck IN_PROGRESS outbox events back to PENDING (staleThreshold={})", reset, staleThreshold);
        }
    }
}
