package org.ticketsouq.reservationservice.stress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.reservationservice.integration.AbstractIntegrationTest;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


@Disabled
class OutboxLockStressTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID outboxEventId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(UUID.randomUUID().toString())
                .eventType("com.example.TestEvent")
                .topic("test-topic")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
            outboxEventRepository.save(event);
            outboxEventId = event.getId();
        });
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.executeWithoutResult(status -> outboxEventRepository.deleteAll());
    }

    @Test
    @DisplayName("10 concurrent claims: only one thread successfully claims the outbox event")
    void concurrentOutboxClaim_onlyOneClaimantWins() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger winCount = new AtomicInteger(0);
        AtomicInteger loseCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int claimed = transactionTemplate.execute(status ->
                        outboxEventRepository.markInProgress(outboxEventId)
                    );
                    if (claimed == 1) {
                        winCount.incrementAndGet();
                    } else {
                        loseCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    loseCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(winCount.get()).isEqualTo(1);
        assertThat(loseCount.get()).isEqualTo(threadCount - 1);

        OutboxEvent updated = transactionTemplate.execute(status ->
            outboxEventRepository.findById(outboxEventId)
        ).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
        assertThat(updated.getClaimedAt()).isNotNull();
    }

    @Test
    @DisplayName("20 events x 5 threads: each event is claimed exactly once (no double delivery)")
    void concurrentOutboxPublish_noDoubleDelivery() throws Exception {
        int eventCount = 20;
        UUID[] eventIds = new UUID[eventCount];

        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < eventCount; i++) {
                OutboxEvent event = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(UUID.randomUUID().toString())
                    .eventType("com.example.TestEvent")
                    .topic("test-topic")
                    .payload("{}")
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();
                outboxEventRepository.save(event);
                eventIds[i] = event.getId();
            }
        });

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalClaimed = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (UUID eventId : eventIds) {
                        int claimed = transactionTemplate.execute(status ->
                            outboxEventRepository.markInProgress(eventId)
                        );
                        if (claimed == 1) {
                            totalClaimed.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(totalClaimed.get()).isEqualTo(eventCount);

        long inProgressCount = transactionTemplate.execute(status ->
            outboxEventRepository.findAll().stream()
                .filter(e -> e.getStatus() == OutboxStatus.IN_PROGRESS)
                .count()
        );
        assertThat(inProgressCount).isEqualTo(eventCount);
    }

    @Test
    @DisplayName("Concurrent reset and claim: no conflict between reset and markInProgress")
    void concurrentOutboxResetAndClaim_noConflict() throws Exception {
        UUID stuckEventId = transactionTemplate.execute(status -> {
            OutboxEvent stuckEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(UUID.randomUUID().toString())
                .eventType("com.example.StuckEvent")
                .topic("test-topic")
                .payload("{}")
                .status(OutboxStatus.IN_PROGRESS)
                .retryCount(0)
                .claimedAt(Instant.now().minus(Duration.ofMinutes(10)))
                .build();
            outboxEventRepository.save(stuckEvent);
            return stuckEvent.getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger resetResult = new AtomicInteger(0);
        AtomicInteger claimResult = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                resetResult.set(transactionTemplate.execute(status ->
                    outboxEventRepository.resetStuckInProgress(5, Instant.now().minus(Duration.ofMinutes(5)))
                ));
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50);
                claimResult.set(transactionTemplate.execute(status ->
                    outboxEventRepository.markInProgress(stuckEventId)
                ));
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        OutboxEvent updated = transactionTemplate.execute(status ->
            outboxEventRepository.findById(stuckEventId)
        ).orElseThrow();
        assertThat(updated.getStatus()).isIn(OutboxStatus.PENDING, OutboxStatus.IN_PROGRESS);
    }
}
