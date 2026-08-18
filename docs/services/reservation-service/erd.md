# Reservation Service — Entity Relationship Diagram

**Database:** `reservation_db` (PostgreSQL)  
**Entities:** Reservation, SagaInstance  
**Pattern:** Saga Orchestration (no JPA relationships — all cross-entity references are detached UUIDs/Strings)

---

## ER Diagram

```mermaid
erDiagram
  %% ── Logical (UUID) relationships ────────────────────────────

  Reservation ||--|| SagaInstance : "drives saga"
  %% Source: reservation-service/src/main/java/.../model/SagaInstance.java:33 (reservationId UUID field)
  %% Source: reservation-service/src/main/java/.../model/SagaInstance.java:18-19 (@UniqueConstraint on reservationId)

  Reservation {
    UUID id PK
    UUID userId "not null"
    UUID eventId "not null"
    enum status "PENDING | COMPLETED | CANCELLED | FAILED"
    instant createdAt
    instant completedAt "nullable"
  }

  SagaInstance {
    UUID id PK
    UUID reservationId "unique, not null"
    UUID userId "not null"
    UUID eventId "not null"
    enum sagaStatus "ACTIVE | COMPLETED | FAILED | COMPENSATING"
    enum currentStep "INITIATED | PAYMENT | TICKET_ISSUANCE | LOCK_CONFIRMATION | COMPLETED | FAILED"
    UUID paymentId "internal Payment UUID, nullable"
    decimal totalAmount "precision 19, scale 2"
    string ticketDetails "jsonb, nullable"
    string failReason "TEXT, nullable"
    int version "optimistic lock"
    instant createdAt
    instant updatedAt
    instant completedAt "nullable"
    instant lastStepCompletedAt "nullable"
  }
```

---

## Entity Definitions

### Reservation
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK (manual) | Reservation ID |
| user_id | UUID | NOT NULL | Purchasing user (detached ref) |
| event_id | UUID | NOT NULL | Target event (detached ref) |
| status | ENUM | NOT NULL | PENDING, COMPLETED, CANCELLED, FAILED |
| created_at | TIMESTAMPTZ | | Auto-set by Spring Data |
| completed_at | TIMESTAMPTZ | nullable | When final status was reached |

### SagaInstance
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK (manual) | Saga instance ID |
| reservation_id | UUID | NOT NULL, UNIQUE | Owning reservation (detached ref) |
| user_id | UUID | NOT NULL | Purchasing user (denormalized) |
| event_id | UUID | NOT NULL | Target event (denormalized) |
| saga_status | ENUM | NOT NULL | ACTIVE, COMPLETED, FAILED, COMPENSATING |
| current_step | ENUM | NOT NULL | INITIATED, PAYMENT, TICKET_ISSUANCE, LOCK_CONFIRMATION, COMPLETED, FAILED |
| payment_id | UUID | nullable | Internal PaymentModel UUID (detached ref) — NOT the Stripe PaymentIntent ID |
| total_amount | DECIMAL(19,2) | nullable | Total reservation amount |
| ticket_details | JSONB | nullable | Snapshot of tickets to issue |
| fail_reason | TEXT | nullable | Failure description |
| version | INT | @Version | Optimistic locking counter |
| created_at | TIMESTAMPTZ | | Auto-set by Spring Data |
| updated_at | TIMESTAMPTZ | | Auto-set by Spring Data |
| completed_at | TIMESTAMPTZ | nullable | Saga completion timestamp |
| last_step_completed_at | TIMESTAMPTZ | nullable | Most recent step completion |

---

## Enum Reference

### ReservationStatus (from `shared-module`)
```java
PENDING, COMPLETED, CANCELLED, FAILED
```

### SagaStatus
```java
ACTIVE, COMPLETED, FAILED, COMPENSATING
```

### SagaStep (Saga orchestrator step machine)
```java
INITIATED, PAYMENT, TICKET_ISSUANCE, LOCK_CONFIRMATION, COMPLETED, FAILED
```

---

## Relationship Summary

| Parent | Child | Type | FK Column / Field | Evidence |
|--------|-------|------|-------------------|----------|
| Reservation | SagaInstance | Logical 1:1 (UUID) | `SagaInstance.reservationId` | `SagaInstance.java:18-19` (@UniqueConstraint), `SagaInstance.java:33` (field) |

> **Note:** `reservation-service` itself contains **zero JPA `@OneToMany`/`@ManyToOne` annotations** — the only entity classes are `Reservation` and `SagaInstance`. All cross-entity references are **detached UUIDs or Strings** — a deliberate design choice to keep saga entities lightweight, avoid unintended cascade locking during distributed transactions, and maintain strict boundary separation.
