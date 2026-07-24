package org.ticketsouq.reservationservice.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.event.OutboxPublisher;
import org.ticketsouq.reservationservice.model.SagaInstance;
import org.ticketsouq.reservationservice.model.enums.SagaStatus;
import org.ticketsouq.reservationservice.repository.ReservationRepository;
import org.ticketsouq.reservationservice.repository.SagaInstanceRepository;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.ReservationService.events.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock private SagaInstanceRepository sagaInstanceRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private SagaOrchestrator sagaOrchestrator;

    private UUID reservationId;
    private UUID userId;
    private UUID eventId;
    private ReservationContext context;

    @BeforeEach
    void setUp() {
        reservationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        List<TicketReservationDto> tickets = List.of(
            new TicketReservationDto(new BigDecimal("50.00"), 1, "A1", "VIP"),
            new TicketReservationDto(new BigDecimal("50.00"), 2, "A2", "VIP")
        );

        context = ReservationContext.builder()
            .reservationId(reservationId)
            .userId(userId)
            .eventId(eventId)
            .tickets(tickets)
            .totalAmount(new BigDecimal("100.00"))
            .build();
    }

    private void stubExecuteWithoutResult() {
        doAnswer(inv -> {
            Consumer<?> callback = inv.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any(Consumer.class));
    }

    @Test
    @DisplayName("New saga: creates saga instance and publishes SagaPaymentCommand")
    void startSaga_newSaga_createsAndAdvances() throws Exception {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.empty());
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        sagaOrchestrator.startSaga(context);

        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_PAYMENT_COMMAND), any(SagaPaymentCommand.class));
    }

    @Test
    @DisplayName("Completed saga: skips saga start, no commands published")
    void startSaga_existingCompletedSaga_doesNothing() throws Exception {
        SagaInstance completedSaga = buildSaga(SagaStatus.COMPLETED, SagaStep.COMPLETED);

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(completedSaga));

        sagaOrchestrator.startSaga(context);

        verify(outboxPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Active saga: resumes from current step, publishes SagaPaymentCommand")
    void startSaga_existingActiveSaga_advancesFromCurrentStep() throws Exception {
        SagaInstance activeSaga = buildSaga(SagaStatus.ACTIVE, SagaStep.INITIATED);

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(activeSaga));

        sagaOrchestrator.startSaga(context);

        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_PAYMENT_COMMAND), any(SagaPaymentCommand.class));
    }

    @Test
    @DisplayName("Payment success: advances to PAYMENT step, publishes SagaTicketCommand")
    void handlePaymentReply_success_advancesToTicketStep() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.INITIATED);
        UUID paymentId = UUID.randomUUID();

        SagaPaymentReplyEvent event = new SagaPaymentReplyEvent(reservationId, paymentId, true, null);

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));

        sagaOrchestrator.handlePaymentReply(event);

        assertThat(saga.getPaymentId()).isEqualTo(paymentId);
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.PAYMENT);
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_TICKET_COMMAND), any(SagaTicketCommand.class));
    }

    @Test
    @DisplayName("Payment failure: compensates with lock release only (no paymentId, no ticket command sent yet)")
    void handlePaymentReply_failure_startsCompensation() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.INITIATED);

        SagaPaymentReplyEvent event = new SagaPaymentReplyEvent(reservationId, null, false, "insufficient funds");

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.handlePaymentReply(event);

        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.FAILED);
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_LOCK_CONFIRM_COMPENSATE), any(SagaLockConfirmCompensateCommand.class));
    }

    @Test
    @DisplayName("Payment reply idempotent: skips when step is already PAYMENT or beyond")
    void handlePaymentReply_alreadyProcessed_skips() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.PAYMENT);

        SagaPaymentReplyEvent event = new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null);

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));

        sagaOrchestrator.handlePaymentReply(event);

        verify(outboxPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Ticket success: advances to TICKET_ISSUANCE step, publishes SagaLockConfirmCommand")
    void handleTicketReply_success_advancesToTicketIssuance() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.PAYMENT);
        saga.setPaymentId(UUID.randomUUID());

        SagaTicketReplyEvent event = new SagaTicketReplyEvent(reservationId, true, null);

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));

        sagaOrchestrator.handleTicketReply(event);

        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.TICKET_ISSUANCE);
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_LOCK_CONFIRM_COMMAND), any(SagaLockConfirmCommand.class));
    }

    @Test
    @DisplayName("Ticket failure: compensates payment refund + lock release (ticket service already rolled back)")
    void handleTicketReply_failure_startsCompensation() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.PAYMENT);
        saga.setPaymentId(UUID.randomUUID());

        SagaTicketReplyEvent event = new SagaTicketReplyEvent(reservationId, false, "seat unavailable");

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.handleTicketReply(event);

        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.FAILED);
        verify(outboxPublisher, never()).publish(eq(reservationId.toString()), eq(SAGA_TICKET_COMPENSATE), any());
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_PAYMENT_COMPENSATE), any(SagaPaymentCompensateCommand.class));
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_LOCK_CONFIRM_COMPENSATE), any(SagaLockConfirmCompensateCommand.class));
    }

    @Test
    @DisplayName("Ticket reply idempotent: skips when step is already TICKET_ISSUANCE or beyond")
    void handleTicketReply_alreadyProcessed_skips() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.TICKET_ISSUANCE);

        SagaTicketReplyEvent event = new SagaTicketReplyEvent(reservationId, true, null);

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));

        sagaOrchestrator.handleTicketReply(event);

        verify(outboxPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Lock confirm success: advances to COMPLETED step, marks saga and reservation done")
    void handleLockConfirmReply_success_completesSaga() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.TICKET_ISSUANCE);

        SagaLockConfirmReplyEvent event = new SagaLockConfirmReplyEvent(reservationId, true, null);

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.handleLockConfirmReply(event);

        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Lock confirm failure: compensates ticket + payment refund + lock release")
    void handleLockConfirmReply_failure_startsCompensation() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.TICKET_ISSUANCE);

        SagaLockConfirmReplyEvent event = new SagaLockConfirmReplyEvent(reservationId, false, "lock expired");

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(saga));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.handleLockConfirmReply(event);

        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_LOCK_CONFIRM_COMPENSATE), any(SagaLockConfirmCompensateCommand.class));
    }

    @Test
    @DisplayName("Payment reply saga not found: silently ignored")
    void handlePaymentReply_sagaNotFound_doesNothing() {
        SagaPaymentReplyEvent event = new SagaPaymentReplyEvent(reservationId, UUID.randomUUID(), true, null);

        stubExecuteWithoutResult();
        when(sagaInstanceRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.handlePaymentReply(event);

        verify(outboxPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Compensate at TICKET_ISSUANCE: sends ticket + payment compensate + lock release")
    void compensate_atTicketStep_publishesTicketAndPaymentCompensate() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.TICKET_ISSUANCE);
        saga.setPaymentId(UUID.randomUUID());

        stubExecuteWithoutResult();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.startCompensation(saga, "test reason");

        assertThat(saga.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.FAILED);
        assertThat(saga.getFailReason()).isEqualTo("test reason");
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_TICKET_COMPENSATE), any(SagaTicketCompensateCommand.class));
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_PAYMENT_COMPENSATE), any(SagaPaymentCompensateCommand.class));
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_LOCK_CONFIRM_COMPENSATE), any(SagaLockConfirmCompensateCommand.class));
    }

    @Test
    @DisplayName("Compensate at PAYMENT: sends only payment compensate + lock release (tickets not yet confirmed)")
    void compensate_atPaymentStep_publishesOnlyPaymentCompensate() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.PAYMENT);
        saga.setPaymentId(UUID.randomUUID());

        stubExecuteWithoutResult();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.startCompensation(saga, "payment failed");

        verify(outboxPublisher, never()).publish(eq(reservationId.toString()), eq(SAGA_TICKET_COMPENSATE), any());
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_PAYMENT_COMPENSATE), any(SagaPaymentCompensateCommand.class));
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_LOCK_CONFIRM_COMPENSATE), any(SagaLockConfirmCompensateCommand.class));
    }

    @Test
    @DisplayName("Compensate at INITIATED: sends only lock release (no payment or ticket confirmation yet)")
    void compensate_atInitiatedStep_publishesOnlyReservationCancelled() throws Exception {
        SagaInstance saga = buildSaga(SagaStatus.ACTIVE, SagaStep.INITIATED);

        stubExecuteWithoutResult();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        sagaOrchestrator.startCompensation(saga, "initiated failed");

        verify(outboxPublisher, never()).publish(eq(reservationId.toString()), eq(SAGA_TICKET_COMPENSATE), any());
        verify(outboxPublisher, never()).publish(eq(reservationId.toString()), eq(SAGA_PAYMENT_COMPENSATE), any());
        verify(outboxPublisher).publish(eq(reservationId.toString()), eq(SAGA_LOCK_CONFIRM_COMPENSATE), any(SagaLockConfirmCompensateCommand.class));
    }

    private SagaInstance buildSaga(SagaStatus status, SagaStep step) {
        return SagaInstance.builder()
            .id(UUID.randomUUID())
            .reservationId(reservationId)
            .userId(userId)
            .eventId(eventId)
            .sagaStatus(status)
            .currentStep(step)
            .totalAmount(new BigDecimal("100.00"))
            .ticketDetails("[]")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .lastStepCompletedAt(Instant.now())
            .build();
    }
}
