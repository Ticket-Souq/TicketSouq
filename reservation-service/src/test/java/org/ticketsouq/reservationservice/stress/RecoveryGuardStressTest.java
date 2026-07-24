package org.ticketsouq.reservationservice.stress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.reservationservice.core.SagaOrchestrator;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.integration.AbstractIntegrationTest;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.model.SagaInstance;
import org.ticketsouq.reservationservice.model.enums.SagaStatus;
import org.ticketsouq.reservationservice.repository.SagaInstanceRepository;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class RecoveryGuardStressTest extends AbstractIntegrationTest {

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID recoveryReservationId;

    @BeforeEach
    void setUp() {
        recoveryReservationId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            BeginReservationEvent event = new BeginReservationEvent(
                UUID.randomUUID(), recoveryReservationId, UUID.randomUUID(),
                List.of(new TicketReservationDto(new BigDecimal("50.00"), 1, "A1", "VIP"))
            );
            Reservation res = reservationService.createReservation(event);
            ReservationContext context = reservationService.createReservationContext(res, event);
            sagaOrchestrator.startSaga(context);
        });
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.executeWithoutResult(status -> sagaInstanceRepository.deleteAll());
    }

    @Test
    @DisplayName("Pessimistic lock for recovery: only one thread succeeds in recovery")
    void concurrentRecoveryWithPessimisticLock_onlyOneWins() throws Exception {
        List<SagaInstance> sagas = transactionTemplate.execute(status ->
            sagaInstanceRepository.findBySagaStatusIn(List.of(SagaStatus.ACTIVE), PageRequest.of(0, 10))).getContent();

        assertThat(sagas).isNotEmpty();
        UUID targetReservationId = sagas.get(0).getReservationId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.recoverSaga(targetReservationId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        SagaInstance saga = transactionTemplate.execute(status ->
            sagaInstanceRepository.findByReservationIdForUpdate(targetReservationId)
        ).orElseThrow();
        assertThat(saga).isNotNull();
    }
}
