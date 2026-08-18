# Payment Service — Entity Relationship Diagram

**Database:** `payment_db` (PostgreSQL)  
**Entities:** PaymentModel, Payout  
**Relationship Style:** All cross-entity references are detached UUIDs — zero JPA relationships.

---

## ER Diagram

```mermaid
erDiagram
  %% No JPA relationships exist between PaymentModel and Payout.
  %% They are logically linked via reservationid -> saga context.
  %% Column names are PHYSICAL (Flyway V1__init.sql); Java field names differ
  %% (e.g. reservationID, customerID, stripePaymentIntentId).

  PaymentModel {
    UUID id PK "auto-generated"
    UUID reservationid "detached ref -> Reservation.id"
    UUID customerid "detached ref -> User.id"
    decimal amount "numeric(38, 2)"
    enum payment_status "PENDING | SUCCESS | FAILED | REFUNDED"
    string transaction_ref "nullable"
    string stripe_payment_intent_id "nullable"
    instant created_at
    instant updated_at
  }

  Payout {
    UUID id PK "auto-generated"
    UUID organizer_id "detached ref -> User.id"
    decimal amount "numeric(38, 2)"
    string currency "nullable"
    string status "PENDING | COMPLETED | FAILED"
    string stripe_transfer_id "nullable"
    instant created_at
    instant updated_at
  }
```

---

## Entity Definitions

### PaymentModel — table `payment_model`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Payment record ID |
| reservationid | UUID | nullable | Logical FK → Reservation.id (saga correlation). Java field `reservationID` |
| customerid | UUID | nullable | Logical FK → User.id (payer). Java field `customerID` |
| amount | DECIMAL(38,2) | nullable | Payment amount |
| payment_status | VARCHAR(255) | nullable | PENDING, SUCCESS, FAILED, REFUNDED (Java enum `PaymentStatus`) |
| transaction_ref | VARCHAR(255) | nullable | Provider transaction reference |
| stripe_payment_intent_id | VARCHAR(255) | nullable | Stripe PaymentIntent ID |
| created_at | TIMESTAMPTZ | | Auto-set by Hibernate `@CreationTimestamp` |
| updated_at | TIMESTAMPTZ | | Auto-set by Hibernate `@UpdateTimestamp` |

### Payout — table `payout`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Payout record ID |
| organizer_id | UUID | nullable | Logical FK → User.id (org head receiving funds). Java field `organizerId` |
| amount | DECIMAL(38,2) | nullable | Payout amount |
| currency | VARCHAR(255) | nullable | Currency code (e.g. "USD") |
| status | VARCHAR(255) | nullable | Free-text status: PENDING, COMPLETED, FAILED |
| stripe_transfer_id | VARCHAR(255) | nullable | Stripe Transfer ID |
| created_at | TIMESTAMPTZ | | Auto-set by Hibernate `@CreationTimestamp` |
| updated_at | TIMESTAMPTZ | | Auto-set by Hibernate `@UpdateTimestamp` |

---

## Key Design Details

### Saga Correlation via `reservationID`
`PaymentModel.reservationID` links the payment to the **reservation-service** saga:
- The reservation service sends `saga.payment.command` → payment service processes → publishes `saga.payment.reply` back
- `reservationID` is the correlation ID across all saga steps

### Payment Lifecycle
```
PENDING -> SUCCESS -> REFUNDED
        -> FAILED
```

### Payout Status (String, not Enum)
`Payout.status` is a plain `String` rather than a Java enum, providing flexibility for provider-specific status values without code changes.

### Stripe Integration
| Field | Purpose |
|-------|---------|
| `stripePaymentIntentId` | Tracks the Stripe PaymentIntent for charge/refund operations |
| `stripeTransferId` | Tracks the Stripe Transfer for organizer payouts |
| `transactionRef` | Fallback generic reference if Stripe is not used (mock mode) |

### Zero JPA Relationships
Both `PaymentModel` and `Payout` are standalone entities with **no** `@ManyToOne` or `@OneToMany` annotations. Foreign keys (`reservationID`, `customerID`, `organizerId`) are detached UUIDs, keeping the payment service decoupled from upstream services.

---

## Relationship Summary

| Source | Target | Type | FK Field | Evidence |
|--------|--------|------|----------|----------|
| PaymentModel | Reservation (external) | Logical N:1 (UUID) | `reservationID` | `PaymentModel.java:29` |
| PaymentModel | User (external) | Logical N:1 (UUID) | `customerID` | `PaymentModel.java:30` |
| Payout | User (external) | Logical N:1 (UUID) | `organizerId` | `Payout.java:28` |
