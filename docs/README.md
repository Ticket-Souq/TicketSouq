
# TicketSouq Backend — Documentation Index

**Tech Stack:** Spring Boot 4.0.3 | Java 25 | Spring Cloud 2025.1.1 | Apache Kafka 7.6.0 | PostgreSQL 16 | Redis 7 | Elasticsearch 9.2.3

Microservice ticket-resale platform with synchronous (REST/Feign) and asynchronous (Kafka/Saga) communication.

## Navigation
| Directory | Description |
|-----------|-------------|
| [api/](api/) | API contracts, OpenAPI specs, endpoint reference |
| [architecture/](architecture/) | System architecture diagrams and design decisions |
| [database/](database/) | Database schemas, migrations, ERDs |
| [decisions/](decisions/) | Architecture Decision Records (ADRs) |
| [sequence/](sequence/) | Sequence diagrams for key flows |
| [shared-module/](shared-module/) | Common library documentation (constants, DTOs, utils) |
| [services/](services/) | Per-service documentation |
| [services/api-gateway/](services/api-gateway/) | Auth, routing, rate-limiting |
| [services/user-service/](services/user-service/) | Users, organizations, members |
| [services/event-service/](services/event-service/) | Events, sections, seats, search |
| [services/reservation-service/](services/reservation-service/) | Saga orchestrator, cart, outbox |
| [services/ticket-service/](services/ticket-service/) | Ticket lifecycle, event snapshots |
| [services/payment-service/](services/payment-service/) | Payments, refunds, payouts |
| [services/venue-service/](services/venue-service/) | Venues, venue templates |
| [services/notification-service/](services/notification-service/) | Email, in-app notifications |
| [services/audit-service/](services/audit-service/) | Audit logging |
| [services/analytics-service/](services/analytics-service/) | Reporting and analytics |
| [services/config-server/](services/config-server/) | Centralized configuration |
| [services/discovery-server/](services/discovery-server/) | Service registry (Eureka) |
## Contribution Guidelines
> **⚠️ IMPORTANT:** Read [`DOCUMENTATION_GUIDELINES.md`](DOCUMENTATION_GUIDELINES.md) **before** writing or editing any documentation in this repository.
Key requirements:
- **Standard Markdown** (GFM)
- **Mermaid.js only** for diagrams
- **Every diagram arrow** must include a `%% Source:` comment citing the exact file and line number
- **kebab-case** for all files and folders
