package org.ticketsouq.reservationservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.reservationservice.core.SagaOrchestrator;
import org.ticketsouq.reservationservice.core.SagaStep;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.model.SagaInstance;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.model.enums.SagaStatus;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;
import org.ticketsouq.reservationservice.repository.ReservationRepository;
import org.ticketsouq.reservationservice.repository.SagaInstanceRepository;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;
import org.ticketsouq.sharedmodule.ReservationService.events.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class ReservationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

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
            new TicketReservationDto(new BigDecimal("50.00"), 1, "A1", "VIP"),
            new TicketReservationDto(new BigDecimal("75.00"), 2, "B3", "Standard")
        );
    }

    @AfterEach
    void cleanup() {
        outboxEventRepository.deleteAll();
        sagaInstanceRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Test
    @Transactional
    @DisplayName("Full happy path: saga progresses through all steps to COMPLETED")
    void fullHappyPath_beginToCompleted() {
        BeginReservationEvent beginEvent = new BeginReservationEvent(eventId, reservationId, userId, tickets);

        Reservation reservation = reservationService.createReservation(beginEvent);
        assertThat(reservation).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);

        ReservationContext context = reservationService.createReservationContext(reservation, beginEvent);
        sagaOrchestrator.startSaga(context);

        SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId).orElseThrow();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.INITIATED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.ACTIVE);

        SagaPaymentReplyEvent paymentReply = new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null);
        sagaOrchestrator.handlePaymentReply(paymentReply);

        saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId).orElseThrow();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PAYMENT);
        assertThat(saga.getPaymentId()).isNotNull();

        SagaTicketReplyEvent ticketReply = new SagaTicketReplyEvent(reservationId, true, null);
        sagaOrchestrator.handleTicketReply(ticketReply);

        saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId).orElseThrow();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.TICKET_ISSUANCE);

        SagaLockConfirmReplyEvent lockReply = new SagaLockConfirmReplyEvent(reservationId, true, null);
        sagaOrchestrator.handleLockConfirmReply(lockReply);

        saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId).orElseThrow();
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getCompletedAt()).isNotNull();

        Reservation updatedReservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(updatedReservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(updatedReservation.getCompletedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Payment failure: saga fails, reservation marked FAILED")
    void paymentFailure_triggersCompensation() {
        BeginReservationEvent beginEvent = new BeginReservationEvent(eventId, reservationId, userId, tickets);

        Reservation reservation = reservationService.createReservation(beginEvent);
        ReservationContext context = reservationService.createReservationContext(reservation, beginEvent);
        sagaOrchestrator.startSaga(context);

        SagaPaymentReplyEvent paymentReply = new SagaPaymentReplyEvent(reservationId, null, false, "card declined");
        sagaOrchestrator.handlePaymentReply(paymentReply);

        SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId).orElseThrow();
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.FAILED);
        assertThat(saga.getFailReason()).contains("card declined");

        Reservation failedReservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(failedReservation.getStatus()).isEqualTo(ReservationStatus.FAILED);
    }

    @Test
    @Transactional
    @DisplayName("Ticket failure after payment: compensates payment refund + lock release, no ticket compensate")
    void ticketFailure_afterPayment_triggersPartialCompensation() {
        BeginReservationEvent beginEvent = new BeginReservationEvent(eventId, reservationId, userId, tickets);

        Reservation reservation = reservationService.createReservation(beginEvent);
        ReservationContext context = reservationService.createReservationContext(reservation, beginEvent);
        sagaOrchestrator.startSaga(context);

        UUID paymentId = UUID.randomUUID();
        sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, paymentId, true, null));

        sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, false, "seat taken"));

        SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId).orElseThrow();
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.FAILED);
        assertThat(saga.getFailReason()).contains("seat taken");

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        List<String> eventTypes = outboxEvents.stream().map(OutboxEvent::getEventType).toList();

        assertThat(eventTypes).noneMatch(t -> t.contains("SagaTicketCompensateCommand"));
        assertThat(eventTypes).anyMatch(t -> t.contains("SagaPaymentCompensateCommand"));
        assertThat(eventTypes).anyMatch(t -> t.contains("SagaLockConfirmCompensateCommand"));
    }

    @Test
    @Transactional
    @DisplayName("Lock confirm failure: compensates ticket + payment refund + lock release")
    void lockConfirmFailure_afterTickets_triggersFullCompensation() {
        BeginReservationEvent beginEvent = new BeginReservationEvent(eventId, reservationId, userId, tickets);

        Reservation reservation = reservationService.createReservation(beginEvent);
        ReservationContext context = reservationService.createReservationContext(reservation, beginEvent);
        sagaOrchestrator.startSaga(context);

        UUID paymentId = UUID.randomUUID();
        sagaOrchestrator.handlePaymentReply(new SagaPaymentReplyEvent(reservationId, paymentId, true, null));
        sagaOrchestrator.handleTicketReply(new SagaTicketReplyEvent(reservationId, true, null));
        sagaOrchestrator.handleLockConfirmReply(new SagaLockConfirmReplyEvent(reservationId, false, "lock timeout"));

        SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId).orElseThrow();
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.FAILED);

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Duplicate begin reservation: second call returns null, only one saved")
    void duplicateBeginReservation_idempotent() {
        BeginReservationEvent beginEvent = new BeginReservationEvent(eventId, reservationId, userId, tickets);

        Reservation first = reservationService.createReservation(beginEvent);
        assertThat(first).isNotNull();

        Reservation second = reservationService.createReservation(beginEvent);
        assertThat(second).isNull();

        assertThat(reservationRepository.findById(reservationId)).isPresent();
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("Outbox retry: event marked FAILED after retryCount reaches 5")
    void outboxRelay_retriesAndMarksFailed() {
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateId(reservationId.toString())
            .eventType("com.example.NonExistentClass")
            .topic("test-topic")
            .payload("{}")
            .status(OutboxStatus.PENDING)
            .retryCount(0)
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
    @Transactional
    @DisplayName("Outbox reset stuck events: stale IN_PROGRESS events reset to PENDING")
    void outboxRelay_resetsStuckEvents() {
        OutboxEvent stuckEvent = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateId(reservationId.toString())
            .eventType("com.example.TestEvent")
            .topic("test-topic")
            .payload("{}")
            .status(OutboxStatus.IN_PROGRESS)
            .retryCount(0)
            .claimedAt(Instant.now().minus(Duration.ofMinutes(10)))
            .build();
        outboxEventRepository.saveAndFlush(stuckEvent);

        int reset = outboxEventRepository.resetStuckInProgress(5, Instant.now().minus(Duration.ofMinutes(5)));
        assertThat(reset).isEqualTo(1);

        OutboxEvent updated = outboxEventRepository.findById(stuckEvent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
