# Ticket/Seat Locking Flow

**Actors:** Client → LocksController → LockService (capacity check + lock creation with TTL) → Kafka (BeginReservationEvent) → Saga Orchestrator → Lock Confirm/Release

---

## Sequence Diagram

```mermaid
sequenceDiagram
  participant Client
  participant LocksCtrl as "LocksController"
  participant LockSvc as "LockService"
  participant eventDB as "event-db (Postgres)"
  participant EventPub as "EventEventPublisher"
  participant Kafka
  participant SagaOrch as "SagaOrchestrator (reservation-service)"
  participant EventConsumer as "EventEventConsumer"
  participant Jobs as "EventStatusJobs (scheduled)"

  Note over Client,Jobs: ================ PHASE 1: LOCK SEATS (OR ZONE) ================

  alt Seat-level lock
    Client->>LocksCtrl: POST /api/v1/events/locks/{eventId}/seats\n{ seatIds: [uuid, ...] }
    %% Source: event-service/.../Controller/LocksController.java:21-27
  else Zone-level lock
    Client->>LocksCtrl: POST /api/v1/events/locks/{eventId}/zones\n{ zoneId, quantity }
    %% Source: event-service/.../Controller/LocksController.java:29-35
  end

  LocksCtrl->>LockSvc: acquireSeatLocks(eventId, request) / acquireZoneLock(eventId, request)
  %% Source: event-service/.../Controller/LocksController.java:25 / 33

  LockSvc->>eventDB: Load Event (check PUBLISHED, bookingModel)
  %% Source: event-service/.../service/LockService.java:43-51 / 95-103

  alt Seat lock path
    LockSvc->>eventDB: SELECT seats WITH PESSIMISTIC_WRITE\nby seatIds + eventId
    %% Source: event-service/.../service/LockService.java:53
    LockSvc->>eventDB: Check any seat is already BOOKED
    %% Source: event-service/.../service/LockService.java:63-69
    LockSvc->>eventDB: Check any active SeatLock (non-expired) exists
    %% Source: event-service/.../service/LockService.java:71-78
    LockSvc->>eventDB: INSERT SeatLock × N\n(reservationId, seatId, expiresAt = now + TTL)
    %% Source: event-service/.../service/LockService.java:80-88
  else Zone lock path
    LockSvc->>eventDB: SELECT Section WITH PESSIMISTIC_WRITE\nby zoneId + eventId
    %% Source: event-service/.../service/LockService.java:105-106
    LockSvc->>eventDB: SELECT SUM(quantity) of active ZoneLocks for zone
    %% Source: event-service/.../service/LockService.java:108
    LockSvc->>LockSvc: available = section.remainingCapacity - activeSum\ncheck quantity <= available
    %% Source: event-service/.../service/LockService.java:110-114
    LockSvc->>eventDB: INSERT ZoneLock\n(zoneId, reservationId, quantity, expiresAt = now + TTL)
    %% Source: event-service/.../service/LockService.java:116-122
  end

  LockSvc-->>Client: LockSeatsResponse / LockZoneResponse\n{ reservationId, status: "LOCKED", expiresAt }
  %% Source: LocksController:22-26 / 30-34

  Note over Client,Jobs: ================ PHASE 2: RESERVE (BEGIN SAGA) ================

  Client->>LocksCtrl: POST /api/v1/events/locks/reserve\n{ eventId, reservationId }
  %% Source: event-service/.../Controller/LocksController.java:37-41

  LocksCtrl->>LockSvc: reserve(request, userId)
  %% Source: event-service/.../Controller/LocksController.java:39

  LockSvc->>eventDB: Load locks by reservationId\n(SeatLock or ZoneLock)
  %% Source: event-service/.../service/LockService.java:232-233

  alt Zone lock
    LockSvc->>eventDB: Load Section, build TicketReservationDto × quantity
    %% Source: event-service/.../service/LockService.java:238-247
  else Seat locks
    LockSvc->>eventDB: Load Seats with Section, build TicketReservationDto per seat
    %% Source: event-service/.../service/LockService.java:249-258
  end

  LockSvc->>EventPub: Publish BeginReservationEvent (Spring event)
  %% Source: event-service/.../service/LockService.java:245 / 257

  EventPub->>Kafka: BeginReservationEvent (topic: reservation.begin)\n[AFTER_COMMIT]
  %% Source: event-service/.../event/EventEventPublisher.java:79-87

  Kafka->>SagaOrch: BeginReservation → startSaga()
  %% Source: reservation-service/.../event/SagaReplyConsumer.java:31-35

  Note over SagaOrch: Saga advances: PAYMENT → TICKET_ISSUANCE → LOCK_CONFIRMATION

  Note over Client,Jobs: ================ PHASE 3: SAGA — LOCK CONFIRMATION ================

  SagaOrch->>Kafka: SagaLockConfirmCommand (topic: saga.lock.confirm.command)
  %% Source: reservation-service/.../core/SagaOrchestrator.java:195-201

  Kafka->>EventConsumer: Consume SagaLockConfirmCommand
  %% Source: event-service/.../event/EventEventConsumer.java:27-37

  EventConsumer->>LockSvc: confirm(reservationId)
  %% Source: event-service/.../event/EventEventConsumer.java:32

  LockSvc->>eventDB: Load locks WITH PESSIMISTIC_WRITE
  %% Source: event-service/.../service/LockService.java:129-130

  alt Locks expired
    LockSvc->>eventDB: DELETE locks by reservationId
    %% Source: event-service/.../service/LockService.java:149 / 183
    LockSvc->>LockSvc: throw LockExpiredException
    EventConsumer-->>Kafka: SagaLockConfirmReplyEvent(success=false)
    %% Source: event-service/.../event/EventEventConsumer.java:34-36
  else Locks valid
    alt Seat confirm
      LockSvc->>eventDB: UPDATE seats SET status = BOOKED
      %% Source: event-service/.../service/LockService.java:168-169
      LockSvc->>eventDB: UPDATE sections SET remainingCapacity -= count
      %% Source: event-service/.../service/LockService.java:173-176
    else Zone confirm
      LockSvc->>eventDB: UPDATE section SET remainingCapacity -= quantity
      %% Source: event-service/.../service/LockService.java:190
    end
    LockSvc->>eventDB: DELETE locks by reservationId
    %% Source: event-service/.../service/LockService.java:178 / 191
    EventConsumer-->>Kafka: SagaLockConfirmReplyEvent(success=true)
    %% Source: event-service/.../event/EventEventPublisher.java:99-107
  end

  Note over Client,Jobs: ================ PHASE 4: SAGA — COMPENSATION (on rollback) ================

  SagaOrch->>Kafka: SagaLockConfirmCompensateCommand (topic: saga.lock.confirm.compensate)
  %% Source: reservation-service/.../core/SagaOrchestrator.java:242-247

  Kafka->>EventConsumer: Consume SagaLockConfirmCompensateCommand
  %% Source: event-service/.../event/EventEventConsumer.java:39-44

  EventConsumer->>LockSvc: release(reservationId)
  %% Source: event-service/.../event/EventEventConsumer.java:43

  LockSvc->>eventDB: DELETE all SeatLock & ZoneLock by reservationId
  %% Source: event-service/.../service/LockService.java:196-197

  Note over Client,Jobs: ================ PHASE 5: TTL CLEANUP (background) ================

  Jobs->>Jobs: expireSeatLocks() / expireZoneLocks() [every 30s]
  %% Source: event-service/.../jobs/EventStatusJobs.java:22-38

  Jobs->>eventDB: DELETE FROM seat_locks WHERE expires_at < now (LIMIT 500)
  %% Source: event-service/.../repository/SeatLockRepository.java:27-28

  Jobs->>eventDB: DELETE FROM zone_locks WHERE expires_at < now (LIMIT 500)
  %% Source: event-service/.../repository/ZoneLockRepository.java:29-30
```

---

## Detailed Step Breakdown

### Phase 1 — Lock Seats/Zone

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 1a | POST `/locks/{eventId}/seats` or `/zones` | `LocksController.java` | 21-35 |
| 1b | `acquireSeatLocks()` or `acquireZoneLock()` | `LockService.java` | 42 / 94 |
| 1c | Validate event is PUBLISHED + correct BookingModel | `LockService.java` | 46-51 / 98-103 |
| 1d | (Seat) Fetch seats with PESSIMISTIC_WRITE, check booked/locked | `LockService.java` | 53-78 |
| 1e | (Zone) Fetch Section with PESSIMISTIC_WRITE, sum active locks, check capacity | `LockService.java` | 105-114 |
| 1f | Create SeatLock/ZoneLock rows with `expiresAt = now + TTL` | `LockService.java` | 80-88 / 116-122 |
| 1g | Return response with `reservationId`, status `"LOCKED"`, `expiresAt` | `LockService.java` | 90 / 124 |

### Phase 2 — Reserve (Begin Saga)

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 2a | POST `/locks/reserve` | `LocksController.java` | 37-41 |
| 2b | `LockService.reserve()` — load locks, build TicketReservationDto list | `LockService.java` | 231-259 |
| 2c | Publish `BeginReservationEvent` (Spring) → `EventEventPublisher` → Kafka | `LockService.java` | 245/257 |
| 2d | `EventEventPublisher` sends to Kafka topic `reservation.begin` | `EventEventPublisher.java` | 79-87 |
| 2e | `SagaReplyConsumer` consumes → starts saga | `SagaReplyConsumer.java` | 31-35 |

### Phase 3 — Saga Lock Confirmation

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 3a | Saga publishes `SagaLockConfirmCommand` | `SagaOrchestrator.java` | 195-201 |
| 3b | `EventEventConsumer.lockConfirmCommandConsumer()` | `EventEventConsumer.java` | 27-37 |
| 3c | `LockService.confirm()` — load locks with PESSIMISTIC_WRITE | `LockService.java` | 128-143 |
| 3d | Check expiration; if expired, delete locks + throw | `LockService.java` | 147-151 / 182-185 |
| 3e | (Seat) Set seats BOOKED, decrement section capacity, delete locks | `LockService.java` | 145-179 |
| 3f | (Zone) Decrement section capacity, delete lock | `LockService.java` | 181-192 |
| 3g | Publish `SagaLockConfirmReplyEvent` (success/fail) | `EventEventConsumer.java` | 33-36 / `EventEventPublisher.java` 99-107 |

### Phase 4 — Saga Compensation (on rollback)

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 4a | Saga publishes `SagaLockConfirmCompensateCommand` | `SagaOrchestrator.java` | 242-247 |
| 4b | `EventEventConsumer.lockConfirmCompensateConsumer()` | `EventEventConsumer.java` | 41-44 |
| 4c | `LockService.release()` — delete all locks for reservation | `LockService.java` | 195-198 |

### Phase 5 — TTL Cleanup (background)

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 5a | `expireSeatLocks()` runs every 30s | `EventStatusJobs.java` | 22-29 |
| 5b | `expireZoneLocks()` runs every 30s | `EventStatusJobs.java` | 31-38 |
| 5c | Batch delete expired seat locks (max 500) | `SeatLockRepository.java` | 27-28 |
| 5d | Batch delete expired zone locks (max 500) | `ZoneLockRepository.java` | 29-30 |

---

## Lock TTL Lifecycle

```
Lock created ──► Lock is ACTIVE for TTL minutes (default: 10)
                     │
                     ├──► Client reserves → BeginReservationEvent → saga starts
                     │       │
                     │       └──► Saga confirms → seats BOOKED, lock deleted
                     │
                     ├──► Saga compensates → lock deleted (release)
                     │
                     ├──► Client releases (API) → lock deleted
                     │
                     └──► TTL expires → EventStatusJobs deletes (every 30s, batch 500)
```

**Evidence source:** `LockService.java:38` — `@Value("${app.lock.ttl:10}")` (default 10 minutes).

---

## Entity Summary

| Entity | Key Fields | Unique Constraint | Indexes |
|--------|------------|-------------------|---------|
| `SeatLock` | `seatId`, `reservationId`, `expiresAt` | `seatId` (unique) | `expires_at`, `reservation_id` |
| `ZoneLock` | `zoneId`, `reservationId`, `quantity`, `expiresAt` | — | `zone_id`, `expires_at`, `reservation_id` |

**Evidence sources:** `SeatLock.java:11-35`, `ZoneLock.java:11-39`.

---

## Key Evidence Sources

| Component | File | Lines |
|-----------|------|-------|
| Lock endpoints (seats/zones/reserve/confirm/release) | `LocksController.java` | 14-54 |
| LockService.acquireSeatLocks | `LockService.java` | 42-91 |
| LockService.acquireZoneLock | `LockService.java` | 94-125 |
| LockService.confirm | `LockService.java` | 128-143 |
| LockService.confirmSeats | `LockService.java` | 145-179 |
| LockService.confirmZone | `LockService.java` | 181-192 |
| LockService.release | `LockService.java` | 195-198 |
| LockService.reserve | `LockService.java` | 230-262 |
| Lock TTL property (`app.lock.ttl:10`) | `LockService.java` | 38 |
| SeatLock entity | `SeatLock.java` | 11-35 |
| ZoneLock entity | `ZoneLock.java` | 11-39 |
| SeatLockRepository (expiry batch delete) | `SeatLockRepository.java` | 27-28 |
| ZoneLockRepository (active quantity sum) | `ZoneLockRepository.java` | 17-18 |
| ZoneLockRepository (expiry batch delete) | `ZoneLockRepository.java` | 29-30 |
| BeginReservationEvent → Kafka | `EventEventPublisher.java` | 79-87 |
| SagaLockConfirmCommand consumer | `EventEventConsumer.java` | 27-37 |
| SagaLockConfirmCompensate consumer | `EventEventConsumer.java` | 39-44 |
| SagaLockConfirmReplyEvent → Kafka | `EventEventPublisher.java` | 99-107 |
| Saga orchestrator lock command | `SagaOrchestrator.java` | 195-201 |
| Saga orchestrator lock compensate | `SagaOrchestrator.java` | 242-247 |
| Scheduled lock cleanup jobs | `EventStatusJobs.java` | 22-38 |
| Lock-specific exceptions | `LockExceptionHandler.java` | 15-55 |
| Kafka topic constants | `TOPIC_NAMES.java` | 36-38 |
| LockSeatsRequest/Response DTOs | `LockSeatsRequest.java`, `LockSeatsResponse.java` | 8-12 |
| LockZoneRequest/Response DTOs | `LockZoneRequest.java`, `LockZoneResponse.java` | 8-12 |
