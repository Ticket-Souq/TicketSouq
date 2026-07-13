# Notification Service — Design Document

**Project:** Ticketaty (تيكيتاتي)
**Service Owner:** Omar
**Architecture Style:** Event-Driven, Kafka-based, no synchronous cross-service calls in the hot path

---

## 1. Overview

The Notification Service is responsible for sending and managing all system notifications. It supports two channels:

- **Email Notifications**
- **In-App Notifications**

The service operates independently and consumes events published by other services via Kafka. It does **not** make synchronous calls to other services to fulfill its core responsibilities — the one exception is a local, self-owned projection of user emails (see Section 4).

---

## 2. Responsibilities

- Sending email notifications
- Creating in-app notifications
- Persisting notifications in the database
- Managing notification status (Read / Unread)
- Consuming events from Kafka
- Retrying failed email deliveries
- Rendering HTML templates for email messages
- Efficiently processing notifications for a large number of users (up to ~70,000 per event) via batch consumption
- Maintaining a local read-only projection of `user_id → email`

### Out of Scope

- Authentication
- Booking Management
- Payment Processing
- QR Code Generation
- Event Management
- Logging & Monitoring
- Refund Processing

---

## 3. Event Flow (Finalized Design)

The service follows an event-driven choreography — each service reacts to an incoming event, performs its own local work, and publishes the next event in the chain. This is not a saga pattern: there are no compensating transactions. If a later step fails (e.g. a refund fails for some users), nothing needs to be rolled back in an earlier step — failures are handled locally via retry and dead-lettering (Section 8), not via undo actions across services. Each service only publishes data it actually owns — no service is forced to carry data it doesn't need (e.g. Event Service never touches user data).

### 3.1 Registration Flow (simple case)

```
Auth Service --(User Registered { userId, email, name })--> Notification Service
```

The email is already present in the payload, so this is a direct consume-and-send. This event is also consumed by Notification Service to populate its local user-email projection (see Section 4).

### 3.2 Password Reset Flow

```
Auth Service --(Password Reset Requested { userId, email })--> Notification Service
Auth Service --(Password Changed { userId, email })--> Notification Service
```

### 3.3 Event Cancellation Flow (fan-out case)

This is the critical scenario: potentially up to 70,000 affected users per cancelled event.

```
1. Event Service        --(Event Cancelled { eventId })-->            Reservation Service
2. Reservation Service  --(Booking Cancelled { bookingId, userId, eventId }) x N -->  Payment Service
                            (one message per booking, cancels each reservation locally first)
3. Payment Service      --(Payment Refunded { userId, eventId, amount }) x N -->      Notification Service
4. Notification Service consumes each Payment Refunded event:
     - resolves userId -> email via local projection table (no external call)
     - renders event-cancelled.html
     - sends email (with retry)
     - persists in-app notification
```

**Key design decisions:**

- Event Service never learns about users — it only knows `eventId`.
- Reservation Service is the fan-out point, since it's the only service that owns the booking list for an event. It emits **one Kafka message per affected user**, not a batch payload — Kafka itself absorbs the "batching" concern, no service needs custom chunking logic to publish.
- Payment Service processes refunds per-user and emits its own per-user event; it never needs to know user emails.
- Notification Service resolves emails from its own local projection table — not via Feign — because a single cancellation can affect up to 70,000 users, and 70,000 synchronous cross-service calls is not viable.

---

## 4. User Email Projection

To avoid synchronous calls to the User/Auth service at send-time (which would not scale to 70,000-user cancellations) and to avoid other services carrying user data they don't own, Notification Service maintains a local, denormalized read model.

### Table: `user_email_projection`

| Field        | Description                          |
|--------------|---------------------------------------|
| user_id      | User ID (PK)                          |
| email        | User's email address                  |
| created_at   | Timestamp the row was inserted        |

**Population:** Consumes `User Registered` events from Auth Service and inserts a row per user. This is append-only — the system does not support email changes, so no update/consistency logic is required.

**Backfill:** If the Notification Service is deployed after users already exist in Auth Service, a one-time backfill is required (out of scope unless confirmed necessary for the capstone — check with the Auth Service owner whether pre-existing users are a real scenario).

---

## 5. Notification Types

### Email Notifications

| Type                     | Template                |
|--------------------------|--------------------------|
| Registration Confirmation | registration.html       |
| Password Reset            | reset-password.html     |
| Payment Successful        | payment-success.html    |
| Payment Failed            | payment-failed.html     |
| Booking Cancellation      | booking-cancelled.html  |
| Event Cancellation        | event-cancelled.html    |

### In-App Notifications

Stored in the database, displayed on the user's Notifications page.

---

## 6. Database

### Table: `notifications`

| Field       | Description               |
|-------------|----------------------------|
| id          | Notification ID            |
| user_id     | Recipient user              |
| title       | Notification title          |
| message     | Notification content        |
| type        | Notification type           |
| is_read     | Read status                 |
| created_at  | Creation timestamp          |

**Indexing:** composite index on `(user_id, is_read)` to keep the unread-count endpoint fast.

**Idempotency constraint:** unique constraint on `(user_id, event_id, type)` (or `(event_id)` where the relationship is 1:1) to guard against duplicate sends caused by Kafka's at-least-once delivery. On a constraint violation, treat as already-processed and skip — no separate idempotency-tracking table needed.

### Table: `user_email_projection`

See Section 4.

---

## 7. REST APIs

| Method | Endpoint                        | Description                              |
|--------|----------------------------------|-------------------------------------------|
| GET    | `/notifications`                | Retrieve all notifications for the authenticated user |
| GET    | `/notifications/unread-count`   | Retrieve number of unread notifications   |
| PATCH  | `/notifications/{id}/read`      | Mark a specific notification as read      |
| PATCH  | `/notifications/read-all`       | Mark all notifications as read            |

---

## 8. Reliability

### Retry

Applied to critical email sends (refund confirmation, password reset, event cancellation) using **Resilience4j `@Retry`** with exponential backoff, consistent with the rest of the system's Feign/Resilience4j usage. On exhaustion, the failed message is routed to a Kafka **dead-letter topic** (e.g. `notification-events-dlt`) rather than silently dropped.

The in-app notification is always persisted first, independent of email outcome — email delivery is a side effect, not the source of truth for "was the user notified."

### Idempotency

Guarded via the unique DB constraint described in Section 6. Necessary because Kafka's at-least-once delivery can redeliver a message after a consumer restart or rebalance — without a guard, a user could receive duplicate emails (e.g. two refund confirmations), which is especially visible in demo scenarios.

### Ordering

Not required. No notification's correctness depends on arriving before or after another, for the same or different users.

---

## 9. Batch & Scale Handling (Event Cancellation Path)

For the worst case — a single event cancellation affecting up to 70,000 users. Note this section describes how Notification Service *consumes* the stream, not a change to the producer side: Payment Service still publishes one `Payment Refunded` event per user, exactly as described in Section 3.3. "Batch consumption" below refers to Notification Service pulling several of those already-individual messages per Kafka `poll()` call, rather than one at a time — a consumer-side read optimization, not a payload change.

- **Kafka batch consumption:** Notification Service uses a batch `@KafkaListener` configuration to pull messages in chunks (e.g. 200–500 per poll) instead of one at a time.
- **Local email resolution:** each user's email is resolved from the local `user_email_projection` table — no external call per message, no external call per batch.
- **Decoupled sending:** emails are not sent synchronously inside the Kafka listener thread. Sending is offloaded to an async worker pool / internal queue so the listener acknowledges quickly and doesn't stall past `max.poll.interval.ms` (which would otherwise trigger consumer-group rebalancing).
- **Horizontal scaling:** multiple Notification Service instances can run in the same Kafka consumer group. The topic should have enough partitions (3–6) to allow real parallelism. Messages must **not** be keyed by `eventId` (that would route all 70,000 messages from one cancellation to a single partition/instance) — key by `userId` or leave unkeyed for even distribution.
