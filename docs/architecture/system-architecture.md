# System Architecture — TicketSouq Backend

> **Note:** All diagrams use native Mermaid `flowchart LR` for GitHub rendering.
> Every arrow is annotated with `%% Source:` pointing to the exact codebase file.

---

## Diagram 1 — Synchronous Flow (REST / Feign)

```mermaid
flowchart LR
  Client["Client (Web / Mobile)"]

  subgraph Infrastructure
    Config["Config Server\n(:8888)"]
    Discovery["Discovery Server\n(:8761 / Eureka)"]
  end

  subgraph Gateway
    GW["API Gateway\n(:8080)"]
  end

  subgraph Services
    US["User Service"]
    ES["Event Service"]
    VS["Venue Service"]
    TS["Ticket Service"]
    RS["Reservation Service"]
    PS["Payment Service"]
    NS["Notification Service"]
    AS["Audit Service"]
  end

  %% Client → Gateway
  %% Source: docker-compose.yml:417-418 (api-gateway port mapping)
  Client -->|"REST / HTTPS"| GW

  %% Gateway → Config & Discovery
  %% Source: docker-compose.yml:429-431 (gateway depends_on config-server + discovery-server)
  GW -.->|"Spring Cloud Config"| Config
  %% Source: api-gateway/pom.xml:26 (spring-cloud-starter-gateway-server-webmvc)
  GW -.->|"Eureka Client"| Discovery

  %% Gateway → User Service (Feign)
  %% Source: api-gateway/src/main/java/.../client/UserServiceClient.java:10
  GW -->|"Feign /api/v1/private/user"| US

  %% Event Service → User Service (Feign)
  %% Source: event-service/src/main/java/.../Client/UserServiceClient.java:9
  ES -->|"Feign /api/v1/private/user/organization"| US

  %% Notification Service → Event Service (Feign)
  %% Source: notification-service/src/main/java/.../client/EventClient.java:11
  NS -->|"Feign /api/v1/events/{eventId}"| ES

  %% Ticket Service → Event Service (Feign)
  %% Source: ticket-service/src/main/java/.../client/EventServiceClient.java:10
  TS -->|"Feign /api/v1/events/{id}"| ES

  %% User Service → Gateway (Feign)
  %% Source: user-service/src/main/java/.../client/AuthServiceClient.java:10
  US -->|"Feign /api/v1/private/auth"| GW

  %% Gateway → All business services (routed traffic)
  %% Source: api-gateway/pom.xml:26 (spring-cloud-starter-gateway-server-webmvc)
  GW -->|"Route /api/v1/users/**"| US
  GW -->|"Route /api/v1/events/**"| ES
  GW -->|"Route /api/v1/venue/**"| VS
  GW -->|"Route /api/v1/tickets/**"| TS
  GW -->|"Route /api/v1/reservations/**"| RS
  GW -->|"Route /api/v1/notification/**"| NS
  GW -->|"Route /api/v1/audit/**"| AS
```

---

## Diagram 2 — Asynchronous Flow (Kafka)

```mermaid
flowchart LR
  subgraph Producers
    GW_P["API Gateway"]
    US_P["User Service"]
    ES_P["Event Service"]
    RS_P["Reservation Service"]
  end

  subgraph Topics
    T1["user.email-verification"]
    T2["user.password-reset"]
    T3["user.password-change"]
    T4["accounts.generated"]
    T5["event.created"]
    T6["event.activated"]
    T7["event.completed"]
    T8["event.cancelled"]
    T9["payment.success"]
    T10["payment.refunded"]
    T11["reservation.begin"]
    T12["saga.payment.command"]
    T13["saga.payment.reply"]
    T14["saga.payment.compensate"]
    T15["saga.ticket.command"]
    T16["saga.ticket.reply"]
    T17["saga.ticket.compensate"]
    T18["saga.lock.confirm.command"]
    T19["saga.lock.confirm.compensate"]
    T20["saga.lock.confirm.reply"]
    T21["audit.event"]
  end

  subgraph Consumers
    NS_C["Notification Service"]
    AS_C["Audit Service"]
    TS_C["Ticket Service"]
    PS_C["Payment Service"]
    ES_C["Event Service"]
    RS_C["Reservation Service"]
  end

  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:9
  GW_P -->|publishes| T1
  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:10
  GW_P -->|publishes| T2
  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:11
  GW_P -->|publishes| T3
  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:12
  GW_P -->|publishes| T4
  %% Source: api-gateway/src/main/java/.../event/AuthEventPublisher.java:27-28
  GW_P -->|publishes| T21

  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:15
  ES_P -->|publishes| T5
  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:16
  ES_P -->|publishes| T6
  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:17
  ES_P -->|publishes| T7
  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:19
  ES_P -->|publishes| T8
  %% Source: event-service/src/main/java/.../event/EventEventPublisher.java:31-32
  ES_P -->|publishes| T11

  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:22
  PS_C -->|publishes| T9
  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:24
  PS_C -->|publishes| T10

  %% Source: shared-module/src/main/java/.../Constants/TOPIC_NAMES.java:28-38
  RS_P -->|publishes| T12
  RS_P -->|publishes| T14
  RS_P -->|publishes| T15
  RS_P -->|publishes| T17
  RS_P -->|publishes| T18
  RS_P -->|publishes| T19

  %% Source: user-service/src/main/java/.../event/UserEventPublisher.java:24-25
  US_P -->|publishes| T21

  %% Consumers
  %% Source: notification-service/src/main/java/.../event/NotificationEventConsumer.java:24
  T1 -->|consumes| NS_C
  %% Source: notification-service/src/main/java/.../event/NotificationEventConsumer.java:36
  T2 -->|consumes| NS_C
  %% Source: notification-service/src/main/java/.../event/NotificationEventConsumer.java:41
  T3 -->|consumes| NS_C
  %% Source: notification-service/src/main/java/.../event/NotificationEventConsumer.java:30
  T9 -->|consumes| NS_C
  %% Source: notification-service/src/main/java/.../event/NotificationEventConsumer.java:24
  T10 -->|consumes| NS_C
  %% Source: notification-service/src/main/java/.../event/NotificationEventConsumer.java:51
  T4 -->|consumes| NS_C

  %% Source: audit-service/src/main/java/.../consumer/AuditEventConsumer.java:20
  T21 -->|consumes| AS_C

  %% Source: ticket-service/src/main/java/.../listener/EventSnapshotConsumer.java:27
  T5 -->|consumes| TS_C
  %% Source: ticket-service/src/main/java/.../listener/EventSnapshotConsumer.java:33
  T6 -->|consumes| TS_C
  %% Source: ticket-service/src/main/java/.../listener/EventSnapshotConsumer.java:39
  T7 -->|consumes| TS_C
  %% Source: ticket-service/src/main/java/.../listener/EventSnapshotConsumer.java:45
  T8 -->|consumes| TS_C
  %% Source: ticket-service/src/main/java/.../listener/SagaTicketCommandConsumer.java:26
  T15 -->|consumes| TS_C
  %% Source: ticket-service/src/main/java/.../listener/SagaTicketCompensateConsumer.java:21
  T17 -->|consumes| TS_C

  %% Source: payment-service/src/main/java/.../listener/SagaPaymentCommandConsumer.java:33
  T12 -->|consumes| PS_C
  %% Source: payment-service/src/main/java/.../listener/SagaPaymentCompensateConsumer.java:25
  T14 -->|consumes| PS_C

  %% Source: event-service/src/main/java/.../event/EventEventConsumer.java:27
  T18 -->|consumes| ES_C
  %% Source: event-service/src/main/java/.../event/EventEventConsumer.java:39
  T19 -->|consumes| ES_C
  %% Source: reservation-service/src/main/java/.../event/SagaReplyConsumer.java:31
  T11 -->|consumes| RS_C

  %% Source: reservation-service/src/main/java/.../event/SagaReplyConsumer.java:41
  T13 -->|consumes| RS_C
  %% Source: reservation-service/src/main/java/.../event/SagaReplyConsumer.java:52
  T16 -->|consumes| RS_C
  %% Source: reservation-service/src/main/java/.../event/SagaReplyConsumer.java:63
  T20 -->|consumes| RS_C
```

---

## Diagram 3 — Data & Infrastructure

```mermaid
flowchart LR
  subgraph Microservices
    GW["API Gateway"]
    US["User Service"]
    ES["Event Service"]
    VS["Venue Service"]
    TS["Ticket Service"]
    PS["Payment Service"]
    RS["Reservation Service"]
    NS["Notification Service"]
    AS["Audit Service"]
    AnS["Analytics Service"]
  end

  subgraph Databases
    DB_Auth["auth-db (Postgres)\n:5432"]
    DB_User["user-db (Postgres)\n:5438"]
    DB_Event["event-db (Postgres)\n:5434"]
    DB_Venue["venue-db (Postgres)\n:5435"]
    DB_Ticket["ticket-db (Postgres)\n:5439"]
    DB_Payment["payment-db (Postgres)\n:5436"]
    DB_Reservation["reservation-db (Postgres)\n:5437"]
    DB_Notification["notification-db (Postgres)\n:5433"]
    DB_Audit["audit-db (Postgres)\n:5440"]
    DB_Analytics["analytics-db (Postgres)\n:5441"]
  end

  subgraph Cache
    Redis["Redis 7\n:6379"]
  end

  subgraph Search
    ES_Engine["Elasticsearch 9\n:9200"]
    Kibana["Kibana 9\n:5601"]
  end

  subgraph Observability
    Prom["Prometheus\n:9090"]
    Loki["Loki\n:3100"]
    Tempo["Tempo\n:3200"]
    Grafana["Grafana\n:3000"]
  end

  subgraph Broker
    Kafka["Kafka 7.6.0\n:29092"]
    KafkaUI["Kafka UI\n:8086"]
  end

  %% Service → Database (PostgreSQL)
  %% Source: docker-compose.yml:48-50 (auth_db), docker-compose.yml:5-24 (auth-db)
  GW --> DB_Auth
  %% Source: docker-compose.yml:25-44 (user-db)
  US --> DB_User
  %% Source: docker-compose.yml:105-124 (event-db)
  ES --> DB_Event
  %% Source: docker-compose.yml:85-104 (venue-db)
  VS --> DB_Venue
  %% Source: docker-compose.yml:125-144 (ticket-db)
  TS --> DB_Ticket
  %% Source: docker-compose.yml:65-84 (payment-db)
  PS --> DB_Payment
  %% Source: docker-compose.yml:165-183 (reservation-db)
  RS --> DB_Reservation
  %% Source: docker-compose.yml:45-64 (notification-db)
  NS --> DB_Notification
  %% Source: docker-compose.yml:145-164 (audit-db)
  AS --> DB_Audit
  %% Source: docker-compose.yml:184-203 (analytics-db)
  AnS --> DB_Analytics

  %% Service → Redis
  %% Source: docker-compose.yml:309-324 (redis)
  GW --> Redis

  %% Service → Elasticsearch
  %% Source: config-server/src/main/resources/config-repo/event-service/event-service.yaml:19-22
  ES --> ES_Engine
  ES_Engine --> Kibana

  %% Observability connections
  %% Source: docker-compose.yml:252-305
  GW -.-> Prom
  US -.-> Prom
  ES -.-> Prom
  VS -.-> Prom
  TS -.-> Prom
  PS -.-> Prom
  RS -.-> Prom
  NS -.-> Prom
  AS -.-> Prom
  AnS -.-> Prom

  Prom --> Grafana
  Loki --> Grafana
  Tempo --> Grafana

  %% Kafka
  %% Source: docker-compose.yml:207-248
  GW -.-> Kafka
  US -.-> Kafka
  ES -.-> Kafka
  TS -.-> Kafka
  PS -.-> Kafka
  RS -.-> Kafka
  NS -.-> Kafka
  AS -.-> Kafka
  Kafka --> KafkaUI
```

---

## Component Definitions

| Component | Port | Description | Source |
|-----------|------|-------------|--------|
| API Gateway | `:8080` | Auth, routing, rate-limiting, JWT | `api-gateway/pom.xml` |
| Config Server | `:8888` | Centralized config (native profile) | `config-server/pom.xml` |
| Discovery Server | `:8761` | Eureka service registry | `discovery-server/pom.xml` |
| User Service | dynamic | Users, organizations, members | `user-service/pom.xml` |
| Event Service | dynamic | Events, sections, seats, search (PG/ES) | `event-service/pom.xml` |
| Venue Service | dynamic | Venues and venue templates | `venue-service/pom.xml` |
| Ticket Service | dynamic | Ticket lifecycle, event snapshots | `ticket-service/pom.xml` |
| Payment Service | dynamic | Payments, refunds, payouts (Stripe/mock) | `payment-service/pom.xml` |
| Reservation Service | dynamic | Saga orchestrator, outbox pattern | `reservation-service/pom.xml` |
| Notification Service | dynamic | Email (SMTP), in-app notifications | `notification-service/pom.xml` |
| Audit Service | dynamic | Immutable audit log | `audit-service/pom.xml` |
| Analytics Service | dynamic | Reporting (currently commented out) | `analytics-service/pom.xml` |
| Kafka | `:29092` | Event broker (Confluent 7.6.0) | `docker-compose.yml:207-248` |
| Redis | `:6379` | Session cache, token store | `docker-compose.yml:309-324` |
| Elasticsearch | `:9200` | Full-text search engine | `docker-compose.yml:328-350` |
| PostgreSQL (×10) | `:5432-5441` | Per-service databases | `docker-compose.yml:3-203` |
| Prometheus | `:9090` | Metrics collection | `docker-compose.yml:252-263` |
| Grafana | `:3000` | Metrics dashboard | `docker-compose.yml:288-305` |

## Communication Matrix

| From | To | Protocol | Async/Sync | Purpose |
|------|----|----------|------------|---------|
| API Gateway | User Service | Feign (REST) | Sync | Create user, check ban status, generate members |
| User Service | API Gateway | Feign (REST) | Sync | Unlock org account |
| Event Service | User Service | Feign (REST) | Sync | Get organization name |
| Notification Service | Event Service | Feign (REST) | Sync | Get event details |
| Ticket Service | Event Service | Feign (REST) | Sync | Get event snapshot |
| API Gateway | Kafka | Produce | Async | Auth events (email verification, password reset, audit) |
| User Service | Kafka | Produce | Async | Audit events |
| Event Service | Kafka | Produce | Async | Event lifecycle events, reservation begin |
| Reservation Service | Kafka | Produce | Async | Saga commands/compensations |
| Payment Service | Kafka | Produce/Consume | Async | Payment success/failure events, saga commands |
| Ticket Service | Kafka | Consume | Async | Event snapshots, saga commands |
| Notification Service | Kafka | Consume | Async | Email triggers (registration, payment, password) |
| Audit Service | Kafka | Consume | Async | Immutable audit log |
| Event Service | Kafka | Consume | Async | Saga lock confirm commands |
| Reservation Service | Kafka | Consume | Async | Saga replies from payment, ticket, lock services |
