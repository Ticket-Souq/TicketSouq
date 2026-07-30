# TicketSouq — Documentation Index

> Central entry point for all architectural documentation. Use this as your onboarding landing page.

---

## System Overview & Architecture

| Document | Description |
|----------|-------------|
| [Documentation Guidelines](DOCUMENTATION_GUIDELINES.md) | Standards and conventions for all docs in this repository |
| [System Architecture](architecture/system-architecture.md) | Overall architecture: services, communication patterns (sync/async), Outbox pattern, sagas |
| [Config Server](infrastructure/config-server.md) | Centralised configuration — Spring Cloud Config, `config-repo/` structure, shared defaults |
| [Discovery Server](infrastructure/discovery-server.md) | Netflix Eureka service registry — registration, lease renewal, per-environment config |
| [Shared Module](shared-module/README.md) | Library JAR — DTOs, Events, Exceptions, Constants, Utilities shared across all services |

---

## Database Schemas & ERDs

### Global

| Document | Description |
|----------|-------------|
| [Global ERD](database/global-erd.md) | Cross-service logical entity relationships and key references |

### Per-Service ERDs

| Service | Document | Description |
|---------|----------|-------------|
| Audit Service | [Audit Service ERD](services/audit-service/erd.md) | Audit logs, events history |
| Event Service | [Event Service ERD](services/event-service/erd.md) | Events, seats, sections, zones, seat/zone locks |
| Notification Service | [Notification Service ERD](services/notification-service/erd.md) | In-app notifications, email jobs, email projections |
| Payment Service | [Payment Service ERD](services/payment-service/erd.md) | Payment records, Stripe integration, refunds |
| Reservation Service | [Reservation Service ERD](services/reservation-service/erd.md) | Reservations, saga instances, outbox events |
| Ticket Service | [Ticket Service ERD](services/ticket-service/erd.md) | Tickets, event snapshots, ticket cancellation |
| User Service | [User Service ERD](services/user-service/erd.md) | Users, organizations, org members |
| Venue Service | [Venue Service ERD](services/venue-service/erd.md) | Venues, sections/zones, seats |

---

## Core Workflows & Sequence Diagrams

| Document | Description |
|----------|-------------|
| [Ticket Purchase Saga](sequence/ticket-purchase-saga.md) | Full saga orchestrating payment → ticket issuance → lock confirmation with Outbox pattern |
| [Event Creation](sequence/event-creation.md) | Creating an event: validation, persistence, search indexing, Kafka events, notification to venue |
| [User Registration & Email Verification](sequence/user-registration.md) | Registration with conditional ORG_HEAD/CUSTOMER flows, OTP generation, email verification |
| [Organization Approval](sequence/org-approval.md) | Admin approves a pending org: credential unlock, status update, dual audit events |
| [Ticket Refund](sequence/ticket-refund.md) | Saga compensation flow: ticket cancellation, payment refund (Stripe API), lock release |
| [Password Reset](sequence/password-reset.md) | OTP-based forgot/reset: Redis-stored OTP, Kafka-driven email, session invalidation |
| [Ticket/Seat Locking](sequence/ticket-lock.md) | Seat/zone lock acquisition with TTL, reserve→saga handoff, confirm/compensate/release, background TTL cleanup |

---

## Document Map

```
docs/
├── README.md                          ◄── YOU ARE HERE
├── DOCUMENTATION_GUIDELINES.md
│
├── architecture/
│   └── system-architecture.md
│
├── database/
│   └── global-erd.md
│
├── infrastructure/
│   ├── config-server.md
│   └── discovery-server.md
│
├── shared-module/
│   └── README.md
│
├── services/
│   ├── audit-service/erd.md
│   ├── event-service/erd.md
│   ├── notification-service/erd.md
│   ├── payment-service/erd.md
│   ├── reservation-service/erd.md
│   ├── ticket-service/erd.md
│   ├── user-service/erd.md
│   └── venue-service/erd.md
│
└── sequence/
    ├── event-creation.md
    ├── org-approval.md
    ├── password-reset.md
    ├── ticket-lock.md
    ├── ticket-purchase-saga.md
    ├── ticket-refund.md
    └── user-registration.md
```

---

*22 documentation files across 6 categories — all links verified against actual on-disk paths.*
