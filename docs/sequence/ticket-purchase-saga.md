# Ticket Purchase — Saga Orchestration (Happy Path)

**Pattern:** Choreography via Kafka with centralized Saga Orchestrator (Reservation Service)  
**Key Mechanism:** Outbox Pattern — all saga commands are written to an outbox table first, then relayed to Kafka by a scheduled `OutboxRelay`.

---

## Sequence Diagram

```mermaid
sequenceDiagram
  participant Client as "Client / API Gateway"
  participant ES as "Event Service"
  participant Kafka as "Kafka"
  participant RS as "Reservation Service\n(Saga Orchestrator)"
  participant PS as "Payment Service"
  participant TS as "Ticket Service"

  Note over Client,TS: ==================== 1. INITIATE ====================

  Client->>ES: POST /api/v1/events/locks/reserve

  ES->>Kafka: BeginReservationEvent (topic: reservation.begin)
  %% Source: event-service/.../event/EventEventPublisher.java:80-87

  Kafka->>RS: Consume BeginReservationEvent
  %% Source: reservation-service/.../event/SagaReplyConsumer.java:31

  RS->>RS: Create Reservation (PENDING) + SagaInstance (ACTIVE/INITIATED)
  %% Source: reservation-service/.../core/SagaOrchestrator.java:53-62 (startSaga)

  Note over RS: Outbox: SagaPaymentCommand queued
  %% Source: reservation-service/.../core/SagaOrchestrator.java:176-182 (advanceSaga -> INITIATED)

  Note over RS: OutboxRelay polls every 2s -> sends to Kafka
  %% Source: reservation-service/.../event/OutboxRelay.java:31-50

  RS->>Kafka: SagaPaymentCommand (topic: saga.payment.command)

  Note over RS,Kafka: ==================== 2. PAYMENT ====================

  Kafka->>PS: Consume SagaPaymentCommand
  %% Source: payment-service/.../listener/SagaPaymentCommandConsumer.java:33-34

  PS->>PS: Process payment (mock/Stripe), save PaymentModel
  %% Source: payment-service/.../listener/SagaPaymentCommandConsumer.java:57-73

  PS->>Kafka: SagaPaymentReplyEvent (topic: saga.payment.reply)
  %% Source: payment-service/.../listener/SagaPaymentCommandConsumer.java:96-99

  Kafka->>RS: Consume SagaPaymentReplyEvent
  %% Source: reservation-service/.../event/SagaReplyConsumer.java:41-42

  RS->>RS: Handle reply -> success -> advance step to PAYMENT
  %% Source: reservation-service/.../core/SagaOrchestrator.java:91-117 (handlePaymentReply)

  Note over RS: Outbox: SagaTicketCommand queued
  %% Source: reservation-service/.../core/SagaOrchestrator.java:186-193 (advanceSaga -> PAYMENT)

  RS->>Kafka: SagaTicketCommand (topic: saga.ticket.command)

  Note over RS,Kafka: ==================== 3. TICKET ISSUANCE ====================

  Kafka->>TS: Consume SagaTicketCommand
  %% Source: ticket-service/.../listener/SagaTicketCommandConsumer.java:26-27

  TS->>TS: CreateTicket records (SeatTicket / ZoneTicket)
  %% Source: ticket-service/.../listener/SagaTicketCommandConsumer.java:31-42

  TS->>Kafka: SagaTicketReplyEvent (topic: saga.ticket.reply)
  %% Source: ticket-service/.../listener/SagaTicketCommandConsumer.java:49-52

  Kafka->>RS: Consume SagaTicketReplyEvent
  %% Source: reservation-service/.../event/SagaReplyConsumer.java:52-53

  RS->>RS: Handle reply -> success -> advance step to TICKET_ISSUANCE
  %% Source: reservation-service/.../core/SagaOrchestrator.java:119-144 (handleTicketReply)

  Note over RS: Outbox: SagaLockConfirmCommand queued
  %% Source: reservation-service/.../core/SagaOrchestrator.java:195-200 (advanceSaga -> TICKET_ISSUANCE)

  RS->>Kafka: SagaLockConfirmCommand (topic: saga.lock.confirm.command)

  Note over RS,Kafka: ==================== 4. LOCK CONFIRMATION ====================

  Kafka->>ES: Consume SagaLockConfirmCommand
  %% Source: event-service/.../event/EventEventConsumer.java:27-29

  ES->>ES: lockService.confirm() -> mark seats locked
  %% Source: event-service/.../event/EventEventConsumer.java:31-36

  ES->>Kafka: SagaLockConfirmReplyEvent (topic: saga.lock.confirm.reply)
  %% Source: event-service/.../event/EventEventPublisher.java:99-107

  Kafka->>RS: Consume SagaLockConfirmReplyEvent
  %% Source: reservation-service/.../event/SagaReplyConsumer.java:63-64

  RS->>RS: Handle reply -> success -> completeSaga()
  %% Source: reservation-service/.../core/SagaOrchestrator.java:146-171 (handleLockConfirmReply)

  RS->>RS: SagaInstance -> COMPLETED, Reservation -> COMPLETED
  %% Source: reservation-service/.../core/SagaOrchestrator.java:295-310 (completeSaga)
```

---

## Saga Step Summary

| Step | Command Topic | Reply Topic | Service | Handler | Key Action |
|------|---------------|-------------|---------|---------|------------|
| 1. Initiate | — | — | Reservation | `SagaReplyConsumer.handleBeginReservation` | Create Reservation + SagaInstance |
| 2. Payment | `saga.payment.command` | `saga.payment.reply` | Payment | `SagaPaymentCommandConsumer` | Process payment via provider, reply success/fail |
| 3. Ticket Issuance | `saga.ticket.command` | `saga.ticket.reply` | Ticket | `SagaTicketCommandConsumer` | Create SeatTicket/ZoneTicket records |
| 4. Lock Confirm | `saga.lock.confirm.command` | `saga.lock.confirm.reply` | Event | `EventEventConsumer.lockConfirmCommandConsumer` | Mark seats as BOOKED, release locks |
| 5. Complete | — | — | Reservation | `SagaOrchestrator.completeSaga` | Mark SagaInstance + Reservation as COMPLETED |

---

## Outbox Pattern Detail

All `OutboxPublisher.publish()` calls write to the `outbox_events` table instead of sending directly to Kafka:

```
SagaOrchestrator.advanceSaga()
  -> OutboxPublisher.publish(aggregateId, topic, event)
    -> INSERT INTO outbox_events (PENDING)
      -> OutboxRelay.publishPendingEvents() [@Scheduled, every 2s]
        -> UPDATE status = IN_PROGRESS
          -> kafkaTemplate.send(topic, key, payload)
            -> on success: UPDATE status = PUBLISHED
            -> on failure: retry (up to max) or reset to PENDING
```

## Compensation Flow (Failure Path)

If any step replies with `success = false`, the orchestrator triggers `startCompensation()`:

```
SagaOrchestrator.startCompensation(saga, reason)
  -> mark SagaStatus = COMPENSATING
  -> reverse-order compensation commands via outbox:
      saga.ticket.compensate   (if tickets issued)
      saga.payment.compensate  (if payment processed)
      saga.lock.confirm.compensate  (always)
  -> mark SagaStatus = FAILED, ReservationStatus = FAILED
```

---

## Key Evidence Sources

| Step | File | Line(s) |
|------|------|---------|
| BeginReservation published | `EventEventPublisher.java` | 80-87 |
| BeginReservation consumed | `SagaReplyConsumer.java` | 31 |
| Saga started | `SagaOrchestrator.java` | 53-62 |
| Advance saga (all steps) | `SagaOrchestrator.java` | 173-209 |
| Outbox write | `OutboxPublisher.java` | 24-45 |
| Outbox relay to Kafka | `OutboxRelay.java` | 31-63 |
| Payment command consumed | `SagaPaymentCommandConsumer.java` | 33-34 |
| Payment reply sent | `SagaPaymentCommandConsumer.java` | 96-99 |
| Payment reply handled | `SagaOrchestrator.java` | 91-117 |
| Ticket command consumed | `SagaTicketCommandConsumer.java` | 26-27 |
| Ticket reply sent | `SagaTicketCommandConsumer.java` | 49-52 |
| Ticket reply handled | `SagaOrchestrator.java` | 119-144 |
| Lock confirm consumed | `EventEventConsumer.java` | 27-29 |
| Lock confirm reply sent | `EventEventPublisher.java` | 99-107 |
| Lock confirm reply handled | `SagaOrchestrator.java` | 146-171 |
| Saga completed | `SagaOrchestrator.java` | 295-310 |
