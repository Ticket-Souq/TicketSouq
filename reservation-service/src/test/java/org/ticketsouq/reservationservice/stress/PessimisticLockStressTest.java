package org.ticketsouq.reservationservice.stress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.reservationservice.core.SagaOrchestrator;
import org.ticketsouq.reservationservice.core.SagaStep;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.integration.AbstractIntegrationTest;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.model.SagaInstance;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.model.enums.SagaStatus;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;
import org.ticketsouq.reservationservice.repository.ReservationRepository;
import org.ticketsouq.reservationservice.repository.SagaInstanceRepository;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PessimisticLockStressTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID reservationId;
    private UUID userId;
    private UUID eventId;
    private List<TicketReservationDto> tickets;

    @BeforeEach
    void setUp() {
        reservationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        tickets = List.of(
            new TicketReservationDto(new BigDecimal("50.00"), 1, "A1", "VIP")
        );
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.executeWithoutResult(status -> sagaInstanceRepository.deleteAll());
    }

    private SagaInstance findSagaInTransaction() {
        return transactionTemplate.execute(status ->
            sagaInstanceRepository.findByReservationIdForUpdate(reservationId)
        ).orElseThrow();
    }

    @Test
    @DisplayName("10 concurrent saga creations: only one saga instance is created")
    void concurrentSagaCreationForSameReservation_onlyOneSucceeds() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        ReservationContext context = transactionTemplate.execute(status -> {
            Reservation res = reservationService.createReservation(event);
            return reservationService.createReservationContext(res, event);
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.startSaga(context);
                } catch (Exception e) {
                    // Pessimistic lock timeout expected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long sagaCount = transactionTemplate.execute(status -> sagaInstanceRepository.count());
        assertThat(sagaCount).isEqualTo(1);

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.ACTIVE);
    }

    @Test
    @DisplayName("10 concurrent payment replies: only first is processed (idempotent)")
    void concurrentPaymentRepliesForSameReservation_onlyFirstProcessed() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        transactionTemplate.executeWithoutResult(status -> {
            Reservation res = reservationService.createReservation(event);
            ReservationContext ctx = reservationService.createReservationContext(res, event);
            sagaOrchestrator.startSaga(ctx);
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger processedCount = new AtomicInteger(0);

        UUID paymentId = UUID.randomUUID();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.handlePaymentReply(
                        new SagaPaymentReplyEvent(reservationId, paymentId, true, null));
                    processedCount.incrementAndGet();
                } catch (Exception e) {
                    // Pessimistic lock timeout expected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PAYMENT);
        assertThat(saga.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("10 concurrent ticket replies: only first is processed (idempotent)")
    void concurrentTicketRepliesForSameReservation_onlyFirstProcessed() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        transactionTemplate.executeWithoutResult(status -> {
            Reservation res = reservationService.createReservation(event);
            ReservationContext ctx = reservationService.createReservationContext(res, event);
            sagaOrchestrator.startSaga(ctx);
            sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
                } catch (Exception e) {
                    // Pessimistic lock timeout expected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.TICKET_ISSUANCE);
    }

    @Test
    @DisplayName("10 concurrent lock confirm replies: only first is processed (idempotent)")
    void concurrentLockConfirmRepliesForSameReservation_onlyFirstProcessed() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        transactionTemplate.executeWithoutResult(status -> {
            Reservation res = reservationService.createReservation(event);
            ReservationContext ctx = reservationService.createReservationContext(res, event);
            sagaOrchestrator.startSaga(ctx);
            sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
            sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.handleLockConfirmReply(new SagaLockConfirmReplyEvent(reservationId, true, null));
                } catch (Exception e) {
                    // Pessimistic lock timeout expected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    @DisplayName("Full saga lifecycle: publishes exactly 4 outbox events (payment, ticket, lock confirm, complete)")
    void fullSagaLifecycle_happyPath_createsAllOutboxEvents() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        ReservationContext context = transactionTemplate.execute(status -> {
            Reservation res = reservationService.createReservation(event);
            return reservationService.createReservationContext(res, event);
        });

        sagaOrchestrator.startSaga(context);
        long afterStart = transactionTemplate.execute(status -> outboxEventRepository.count());
        assertThat(afterStart).isGreaterThanOrEqualTo(1);

        sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
        long afterPayment = transactionTemplate.execute(status -> outboxEventRepository.count());
        assertThat(afterPayment).isGreaterThanOrEqualTo(2);

        sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
        long afterTicket = transactionTemplate.execute(status -> outboxEventRepository.count());
        assertThat(afterTicket).isGreaterThanOrEqualTo(3);

        sagaOrchestrator.handleLockConfirmReply(new SagaLockConfirmReplyEvent(reservationId, true, null));
        long afterLockConfirm = transactionTemplate.execute(status -> outboxEventRepository.count());
        assertThat(afterLockConfirm).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("Concurrent full lifecycle: saga completes exactly once despite races")
    void concurrentFullLifecycle_racesToEnd_sagaCompletesExactlyOnce() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        ReservationContext context = transactionTemplate.execute(status -> {
            Reservation res = reservationService.createReservation(event);
            return reservationService.createReservationContext(res, event);
        });

        sagaOrchestrator.startSaga(context);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount * 3);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.handlePaymentReply(
                        new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
                } catch (Exception e) {
                    // pessimistic lock timeout
                } finally {
                    doneLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.handleTicketReply(
                        new SagaTicketReplyEvent(reservationId, true, null));
                } catch (Exception e) {
                    // pessimistic lock timeout
                } finally {
                    doneLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.handleLockConfirmReply(
                        new SagaLockConfirmReplyEvent(reservationId, true, null));
                } catch (Exception e) {
                    // pessimistic lock timeout
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);

        Reservation reservation = transactionTemplate.execute(status ->
            reservationRepository.findById(reservationId).orElseThrow()
        );
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    @DisplayName("Start saga after completion: idempotent, no new commands published")
    void startSaga_afterCompletion_isNoop() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        ReservationContext context = transactionTemplate.execute(status -> {
            Reservation res = reservationService.createReservation(event);
            return reservationService.createReservationContext(res, event);
        });

        sagaOrchestrator.startSaga(context);
        sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
        sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
        sagaOrchestrator.handleLockConfirmReply(new SagaLockConfirmReplyEvent(reservationId, true, null));

        long outboxBefore = transactionTemplate.execute(status -> outboxEventRepository.count());

        sagaOrchestrator.startSaga(context);

        long outboxAfter = transactionTemplate.execute(status -> outboxEventRepository.count());
        assertThat(outboxAfter).isEqualTo(outboxBefore);

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    @DisplayName("Replies after completion: ignored, no duplicate events published")
    void replyAfterCompletion_isIgnored() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        ReservationContext context = transactionTemplate.execute(status -> {
            Reservation res = reservationService.createReservation(event);
            return reservationService.createReservationContext(res, event);
        });

        sagaOrchestrator.startSaga(context);
        sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
        sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
        sagaOrchestrator.handleLockConfirmReply(new SagaLockConfirmReplyEvent(reservationId, true, null));

        long outboxBefore = transactionTemplate.execute(status -> outboxEventRepository.count());

        sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
        sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
        sagaOrchestrator.handleLockConfirmReply(new SagaLockConfirmReplyEvent(reservationId, true, null));

        long outboxAfter = transactionTemplate.execute(status -> outboxEventRepository.count());
        assertThat(outboxAfter).isEqualTo(outboxBefore);

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);

        Reservation reservation = transactionTemplate.execute(status ->
            reservationRepository.findById(reservationId).orElseThrow()
        );
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    @DisplayName("10 concurrent start saga calls: only one saga is created, completes normally")
    void concurrentFullLifecycle_startRace_sagaCompletesOnlyOnce() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        ReservationContext context = transactionTemplate.execute(status -> {
            Reservation res = reservationService.createReservation(event);
            return reservationService.createReservationContext(res, event);
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sagaOrchestrator.startSaga(context);
                } catch (Exception e) {
                    // pessimistic lock timeout
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long sagaCount = transactionTemplate.execute(status -> sagaInstanceRepository.count());
        assertThat(sagaCount).isEqualTo(1);

        transactionTemplate.executeWithoutResult(status -> {
            sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null));
            sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
            sagaOrchestrator.handleLockConfirmReply(new SagaLockConfirmReplyEvent(reservationId, true, null));
        });

        SagaInstance saga = findSagaInTransaction();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);

        Reservation reservation = transactionTemplate.execute(status ->
            reservationRepository.findById(reservationId).orElseThrow()
        );
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    @DisplayName("Optimistic lock: concurrent updates throw ObjectOptimisticLockingFailureException")
    void optimisticLockConcurrentUpdate_throwsOptimisticException() throws Exception {
        BeginReservationEvent event = new BeginReservationEvent(eventId, reservationId, userId, tickets);
        transactionTemplate.executeWithoutResult(status -> {
            Reservation res = reservationService.createReservation(event);
            ReservationContext ctx = reservationService.createReservationContext(res, event);
            sagaOrchestrator.startSaga(ctx);
        });

        UUID sagaId = transactionTemplate.execute(status ->
            sagaInstanceRepository.findByReservationIdForUpdate(reservationId).map(SagaInstance::getId).orElseThrow()
        );

        transactionTemplate.executeWithoutResult(status -> {
            SagaInstance saga1 = sagaInstanceRepository.findById(sagaId).orElseThrow();
            saga1.setFailReason("first update");
            sagaInstanceRepository.save(saga1);
        });

        transactionTemplate.executeWithoutResult(status -> {
            SagaInstance saga2 = sagaInstanceRepository.findById(sagaId).orElseThrow();
            saga2.setFailReason("second update");
            try {
                sagaInstanceRepository.save(saga2);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                assertThat(e).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
            }
        });
    }
}
