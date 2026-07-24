package org.ticketsouq.reservationservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final OutBoxUtil outBoxUtil;

    @Scheduled(fixedDelay = 2000)
    public void publishPendingEvents() {
        // First, reset stuck events in a separate transaction
        transactionTemplate.executeWithoutResult(status -> {
            outBoxUtil.resetStuckEvents();
        });

        // Then fetch and publish pending events
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING);
            if (pending.isEmpty()) return;

            for (OutboxEvent event : pending) {
                int claimed = outboxEventRepository.markInProgress(event.getId());
                if (claimed == 0) continue;

                try {
                    Object payload = deserializePayload(event);
                    kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                outBoxUtil.handlePublishFailure(event, ex);
                            } else {
                                outBoxUtil.handlePublishSuccess(event);
                            }
                        });
                } catch (Exception e) {
                    outBoxUtil.handlePublishFailure(event, e);
                }
            }
        });
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanPublishedEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        outboxEventRepository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, cutoff);
        log.info("Cleaned published outbox events older than 7 days");
    }

    private Object deserializePayload(OutboxEvent event) {
        try {
            Class<?> eventClass = Class.forName(event.getEventType());
            return objectMapper.readValue(event.getPayload(), eventClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize outbox payload: " + event.getEventType(), e);
        }
    }
}
