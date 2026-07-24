package org.ticketsouq.reservationservice.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.model.SagaInstance;
import org.ticketsouq.reservationservice.model.enums.SagaStatus;
import org.ticketsouq.reservationservice.event.OutboxPublisher;
import org.ticketsouq.reservationservice.repository.ReservationRepository;
import org.ticketsouq.reservationservice.repository.SagaInstanceRepository;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmCompensateCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmReplyEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentCompensateCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentReplyEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketCompensateCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketReplyEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_LOCK_CONFIRM_COMMAND;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_LOCK_CONFIRM_COMPENSATE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_PAYMENT_COMMAND;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_PAYMENT_COMPENSATE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_TICKET_COMMAND;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_TICKET_COMPENSATE;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final ReservationRepository reservationRepository;
    private final OutboxPublisher outboxPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public void startSaga(ReservationContext context) {
        SagaInstance saga = createOrFindSaga(context);

        if (saga.getSagaStatus() == SagaStatus.COMPLETED) return;

        if (saga.getSagaStatus() == SagaStatus.ACTIVE) {
            log.info("Starting/resuming saga {} for reservation {}", saga.getId(), saga.getReservationId());
            advanceSaga(saga, context);
        }
    }

    private SagaInstance createOrFindSaga(ReservationContext context) {
        return transactionTemplate.execute(status -> {
            Optional<SagaInstance> existing = sagaInstanceRepository.findByReservationIdForUpdate(context.getReservationId());
            if (existing.isPresent()) {
                return existing.get();
            }

            SagaInstance saga = SagaInstance.builder()
                .id(UUID.randomUUID())
                .reservationId(context.getReservationId())
                .userId(context.getUserId())
                .eventId(context.getEventId())
                .sagaStatus(SagaStatus.ACTIVE)
                .currentStep(SagaStep.INITIATED)
                .totalAmount(context.getTotalAmount())
                .ticketDetails(toJson(context.getTickets()))
                .build();
            try {
                return sagaInstanceRepository.save(saga);
            } catch (DataIntegrityViolationException e) {
                log.debug("Race condition on saga creation for reservation {}, fetching existing", context.getReservationId());
                return sagaInstanceRepository.findByReservationIdForUpdate(context.getReservationId())
                    .orElseThrow(() -> new RuntimeException("Saga not found after race condition for reservationId: " + context.getReservationId()));
            }
        });
    }

    public void handlePaymentReply(SagaPaymentReplyEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(event.reservationId())
                .orElse(null);
            if (saga == null) {
                log.error("Saga not found for reservationId={} on payment reply", event.reservationId());
                return;
            }
            if (saga.getCurrentStep().ordinal() >= SagaStep.PAYMENT.ordinal()) {
                log.debug("Payment reply already processed for saga {} (currentStep={})", saga.getId(), saga.getCurrentStep());
                return;
            }

            if (event.success()) {
                saga.setPaymentId(event.paymentId());
                saga.setCurrentStep(SagaStep.PAYMENT);
                saga.setLastStepCompletedAt(Instant.now());
                sagaInstanceRepository.save(saga);
                log.info("Payment confirmed for saga {}, advancing to ticket issuance", saga.getId());
                advanceSaga(saga, buildContext(saga));
            } else {
                String reason = "Payment failed: " + event.failReason();
                log.warn("Payment failed for saga {}: {}", saga.getId(), reason);
                startCompensation(saga, reason);
            }
        });
    }

    public void handleTicketReply(SagaTicketReplyEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(event.reservationId())
                .orElse(null);
            if (saga == null) {
                log.error("Saga not found for reservationId={} on ticket reply", event.reservationId());
                return;
            }
            if (saga.getCurrentStep().ordinal() >= SagaStep.TICKET_ISSUANCE.ordinal()) {
                log.debug("Ticket reply already processed for saga {} (currentStep={})", saga.getId(), saga.getCurrentStep());
                return;
            }

            if (event.success()) {
                saga.setCurrentStep(SagaStep.TICKET_ISSUANCE);
                saga.setLastStepCompletedAt(Instant.now());
                sagaInstanceRepository.save(saga);
                log.info("Ticket issuance confirmed for saga {}, advancing to lock confirmation", saga.getId());
                advanceSaga(saga, buildContext(saga));
            } else {
                String reason = "Ticket issuance failed: " + event.failReason();
                log.warn("Ticket issuance failed for saga {}: {}", saga.getId(), reason);
                startCompensation(saga, reason);
            }
        });
    }

    public void handleLockConfirmReply(SagaLockConfirmReplyEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(event.reservationId())
                .orElse(null);
            if (saga == null) {
                log.error("Saga not found for reservationId={} on lock confirm reply", event.reservationId());
                return;
            }
            if (saga.getCurrentStep().ordinal() >= SagaStep.LOCK_CONFIRMATION.ordinal()) {
                log.debug("Lock confirm reply already processed for saga {} (currentStep={})", saga.getId(), saga.getCurrentStep());
                return;
            }

            if (event.success()) {
                saga.setCurrentStep(SagaStep.LOCK_CONFIRMATION);
                saga.setLastStepCompletedAt(Instant.now());
                sagaInstanceRepository.save(saga);
                log.info("Lock confirmed for saga {}, completing saga", saga.getId());
                completeSaga(saga);
            } else {
                String reason = "Lock confirmation failed: " + event.failReason();
                log.warn("Lock confirmation failed for saga {}: {}", saga.getId(), reason);
                startCompensation(saga, reason);
            }
        });
    }

    private void advanceSaga(SagaInstance saga, ReservationContext context) {
        switch (saga.getCurrentStep()) {
            case INITIATED -> {
                SagaPaymentCommand cmd = new SagaPaymentCommand(
                    saga.getReservationId(),
                    saga.getUserId(),
                    saga.getEventId(),
                    saga.getTotalAmount()
                );
                outboxPublisher.publish(saga.getReservationId().toString(), SAGA_PAYMENT_COMMAND, cmd);
                log.info("Published SagaPaymentCommand for saga {}", saga.getId());
            }
            case PAYMENT -> {
                SagaTicketCommand cmd = new SagaTicketCommand(
                    saga.getReservationId(),
                    saga.getEventId(),
                    saga.getUserId(),
                    fromJson(saga.getTicketDetails())
                );
                outboxPublisher.publish(saga.getReservationId().toString(), SAGA_TICKET_COMMAND, cmd);
                log.info("Published SagaTicketCommand for saga {}", saga.getId());
            }
            case TICKET_ISSUANCE -> {
                SagaLockConfirmCommand cmd = new SagaLockConfirmCommand(
                    saga.getReservationId()
                );
                outboxPublisher.publish(saga.getReservationId().toString(), SAGA_LOCK_CONFIRM_COMMAND, cmd);
                log.info("Published SagaLockConfirmCommand for saga {}", saga.getId());
            }
            case LOCK_CONFIRMATION -> {
                log.debug("Saga {} waiting for lock confirmation", saga.getId());
            }
        }
    }

    public void startCompensation(SagaInstance saga, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            saga.setSagaStatus(SagaStatus.COMPENSATING);
            saga.setFailReason(reason);
            sagaInstanceRepository.save(saga);
        });
        compensate(saga);
    }

    private void compensate(SagaInstance saga) {
        SagaStep lastConfirmed = saga.getCurrentStep();

        // Collect all compensation commands first
        List<OutboxCommand> compensationCommands = new ArrayList<>();
        if (lastConfirmed.ordinal() >= SagaStep.TICKET_ISSUANCE.ordinal()) {
            compensationCommands.add(new OutboxCommand(
                saga.getReservationId().toString(),
                SAGA_TICKET_COMPENSATE,
                new SagaTicketCompensateCommand(saga.getReservationId())
            ));
        }

        if (lastConfirmed.ordinal() >= SagaStep.PAYMENT.ordinal()) {
            if (saga.getPaymentId() != null) {
                compensationCommands.add(new OutboxCommand(
                    saga.getReservationId().toString(),
                    SAGA_PAYMENT_COMPENSATE,
                    new SagaPaymentCompensateCommand(saga.getReservationId(), saga.getPaymentId())
                ));
            }
        }
        // release the locks
        compensationCommands.add(new OutboxCommand(
            saga.getReservationId().toString(),
            SAGA_LOCK_CONFIRM_COMPENSATE,
            new SagaLockConfirmCompensateCommand(saga.getReservationId())
        ));

        // Publish all compensation commands in a single transaction, then mark FAILED
        transactionTemplate.executeWithoutResult(status -> {
            for (OutboxCommand cmd : compensationCommands) {
                outboxPublisher.publish(cmd.aggregateId(), cmd.topic(), cmd.event());
                log.info("Published {} for saga {}", cmd.event().getClass().getSimpleName(), saga.getId());
            }

            saga.setSagaStatus(SagaStatus.FAILED);
            saga.setCurrentStep(SagaStep.FAILED);
            saga.setCompletedAt(Instant.now());
            sagaInstanceRepository.save(saga);

            reservationRepository.findById(saga.getReservationId()).ifPresent(r -> {
                r.setStatus(ReservationStatus.FAILED);
                r.setCompletedAt(Instant.now());
                reservationRepository.save(r);
            });
        });
        log.info("Saga {} marked as FAILED after compensation", saga.getId());
    }

    private record OutboxCommand(String aggregateId, String topic, Object event) {}

    public void recoverSaga(UUID reservationId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                SagaInstance saga = sagaInstanceRepository.findByReservationIdForUpdate(reservationId)
                    .orElseThrow(() -> new RuntimeException("Saga instance not found for reservationId: " + reservationId));

                log.info("Recovering saga {} (status={}, step={})", saga.getId(), saga.getSagaStatus(), saga.getCurrentStep());

                ReservationContext context = buildContext(saga);

                if (saga.getSagaStatus() == SagaStatus.COMPENSATING) {
                    compensate(saga);
                    return;
                }

                advanceSaga(saga, context);
            });
        } catch (Exception e) {
            log.warn("Recovery of saga for reservation {} encountered an error (may be idempotent): {}", reservationId, e.getMessage());
        }

    }

    private void completeSaga(SagaInstance saga) {
        transactionTemplate.executeWithoutResult(status -> {
            saga.setCurrentStep(SagaStep.COMPLETED);
            saga.setSagaStatus(SagaStatus.COMPLETED);
            saga.setCompletedAt(Instant.now());
            saga.setLastStepCompletedAt(Instant.now());
            sagaInstanceRepository.save(saga);

            reservationRepository.findById(saga.getReservationId()).ifPresent(r -> {
                r.setStatus(ReservationStatus.COMPLETED);
                r.setCompletedAt(Instant.now());
                reservationRepository.save(r);
            });
        });
        log.info("Saga {} completed successfully", saga.getId());
    }

    private ReservationContext buildContext(SagaInstance saga) {
        return ReservationContext.builder()
            .reservationId(saga.getReservationId())
            .userId(saga.getUserId())
            .eventId(saga.getEventId())
            .tickets(fromJson(saga.getTicketDetails()))
            .totalAmount(saga.getTotalAmount())
            .paymentId(saga.getPaymentId())
            .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to serialize ticket details", e);
            return "[]";
        }
    }

    private List<TicketReservationDto> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to deserialize ticket details", e);
            return List.of();
        }
    }
}
