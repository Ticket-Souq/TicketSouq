# Notification Service — Entity Relationship Diagram

**Database:** `notification_db` (PostgreSQL)  
**Entities:** Notification, EmailJob, UserEmailProjection  
**Pattern:** In-app notifications + async email queue + local read-model for user emails.

---

## ER Diagram

```mermaid
erDiagram
  %% No JPA relationships exist between these entities.
  %% UserEmailProjection is a denormalized read-model populated from Kafka events.

  Notification {
    UUID id PK "auto-generated"
    UUID user_id "detached ref -> User.id"
    Long event_id "detached ref -> Event.id (stored as Long)"
    string title "not null, max 150"
    string message "TEXT, not null"
    enum type "REGISTRATION | PASSWORD_RESET | PASSWORD_CHANGED | PAYMENT_SUCCESS | EVENT_CANCELLED | ACCOUNT_GENERATED"
    boolean is_read "default false"
    datetime created_at
  }

  EmailJob {
    UUID id PK "auto-generated"
    UUID message_id "unique, not null"
    string recipient "not null"
    enum template "REGISTRATION | PASSWORD_RESET | PASSWORD_CHANGED | PAYMENT_SUCCESS | EVENT_CANCELLED | ACCOUNT_GENERATED"
    string variablesJson "TEXT, not null"
    enum status "PENDING | SENT | FAILED"
    int retryCount "default 0"
    datetime lastAttemptAt "nullable"
    datetime createdAt
  }

  UserEmailProjection {
    UUID user_id PK "manual, matches User.id"
    string email "not null, unique"
  }
```

---

## Entity Definitions

### Notification (in-app)
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Notification ID |
| user_id | UUID | NOT NULL | Logical FK → User.id |
| event_id | BIGINT | nullable | Logical FK → Event.id (stored as Long) |
| title | VARCHAR(150) | NOT NULL | Notification title |
| message | TEXT | NOT NULL | Notification body |
| type | ENUM | NOT NULL | REGISTRATION, PASSWORD_RESET, PASSWORD_CHANGED, PAYMENT_SUCCESS, EVENT_CANCELLED, ACCOUNT_GENERATED |
| is_read | BOOLEAN | NOT NULL, default false | Read status, indexed via (user_id, is_read) |
| created_at | TIMESTAMP | NOT NULL | Auto-set by Hibernate |

### EmailJob (async email queue)
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Job ID |
| message_id | UUID | NOT NULL, UNIQUE | Deduplication / idempotency key |
| recipient | VARCHAR(255) | NOT NULL | Target email address |
| template | ENUM | NOT NULL | Email template identifier |
| variables_json | TEXT | NOT NULL | JSON map of template variables |
| status | ENUM | NOT NULL | PENDING, SENT, FAILED |
| retry_count | INT | NOT NULL, default 0 | Retry attempt counter |
| last_attempt_at | TIMESTAMP | nullable | Most recent send attempt |
| created_at | TIMESTAMP | NOT NULL | Auto-set by Hibernate |

### UserEmailProjection (local read-model)
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| user_id | UUID | PK (manual) | Matches User.id from user-service |
| email | VARCHAR(255) | NOT NULL, UNIQUE | Denormalized user email address |

---

## Key Design Details

### UserEmailProjection — Local Read-Model
`UserEmailProjection` is a **denormalized local cache** of user email addresses:
- It is populated by consuming the `accounts.generated` Kafka event from the API Gateway
- It eliminates synchronous Feign calls to user-service when sending notifications
- The PK `user_id` matches `User.id` exactly (manually assigned, same value)
- This is a classic **event-driven read-model** pattern (CQRS-lite)

### Two Notification Channels
| Entity | Channel | Delivery |
|--------|---------|----------|
| `Notification` | In-app | Stored in DB, fetched via REST API (`/api/v1/notification`) |
| `EmailJob` | Email (SMTP) | Queued in `email_jobs`, processed by `EmailScheduler` |

### EmailJob Processing Flow
```
PENDING -> SENT (success)
        -> FAILED -> retry (up to max)
                  -> PENDING (reset by recovery job)
```

### Notification Type / Template Mapping
| Kafka Event | Notification Type | Email Template |
|-------------|-------------------|----------------|
| `user.email-verification` | REGISTRATION | `email/registration.html` |
| `user.password-reset` | PASSWORD_RESET | `email/password-reset.html` |
| `user.password-change` | PASSWORD_CHANGED | `email/password-changed.html` |
| `payment.success` | PAYMENT_SUCCESS | `email/payment-success.html` |
| `payment.refunded` | EVENT_CANCELLED | `email/refund-completed.html` |
| `accounts.generated` | ACCOUNT_GENERATED | `email/account-generated.html` |

### Zero JPA Relationships
All three entities are standalone with **no** `@ManyToOne` or `@OneToMany` annotations. Cross-entity references are detached UUIDs. The notification service is designed to be fully independent of upstream services, communicating only via Kafka events and the local read-model.

---

## Relationship Summary

| Source | Target | Type | FK Field | Evidence |
|--------|--------|------|----------|----------|
| Notification | User (external) | Logical N:1 (UUID) | `userId` | `Notification.java:27-28` |
| Notification | Event (external) | Logical N:1 (Long) | `eventId` | `Notification.java:30-31` |
| UserEmailProjection | User (external) | Logical 1:1 (UUID as PK) | `userId` | `UserEmailProjection.java:17-19` |
| EmailJob | UserEmailProjection | Logical N:1 (String) | `recipient` → `email` | `EmailJob.java:33-34` |
