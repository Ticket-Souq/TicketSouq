# Event Service

Manages event lifecycle, venue layout (zones/seats), search, and temporary lock-based reservation system for the TicketSouq platform.

## Tech Stack

- **Spring Boot 4.0.3** — REST API, JPA, scheduled jobs
- **Spring Data JPA / Hibernate** — PostgreSQL persistence with pessimistic locking
- **Spring Data Elasticsearch** — event search with fuzziness-enabled full-text queries
- **Spring Cloud Config** — centralized configuration from `config-server`
- **Eureka Discovery** — service registration and load-balanced inter-service calls
- **PostgreSQL (pg_trgm)** — primary database with trigram similarity search fallback
- **Elasticsearch** — primary search index (fuzzy matching on title/category/organization)
- **Kafka** — async event publishing (event lifecycle, audit, payout)
- **OpenFeign** — inter-service HTTP calls to `user-service`
- **Lombok** — boilerplate reduction

## Dependencies

| Service | Purpose |
|---------|---------|
| `config-server` | Serves config at startup (port 8888) |
| `discovery-server` | Service registry (Eureka, port 8761) |
| `user-service` | Organization name resolution for event filtering and creation |

Infrastructure required: PostgreSQL (`event_db`, port 5434), Elasticsearch (port 9200), Kafka (port 29092), Prometheus + Loki + Tempo (observability).

## Event Status Lifecycle

```
PUBLISHED ──(startDate)──▶ ACTIVE ──(finishDate)──▶ COMPLETED
     │                        │
     │                        │
     └── CANCELLED ───────────┘
```

Status transitions are handled by `EventService` and driven by precise one-shot tasks scheduled via Spring's `TaskScheduler`.

### Automatic Transitions (`EventStatusScheduler`)

| Event | Timing | Action |
|-------|--------|--------|
| Event created | `@TransactionalEventListener(AFTER_COMMIT)` | Schedules `activateEvent` at `startDate`, `completeEvent` at `finishDate` |
| Event cancelled | `@TransactionalEventListener(AFTER_COMMIT)` | Cancels pending scheduled tasks for that event via `cancelScheduledTasks()` |
| Startup recovery | `@PostConstruct` | Queries all `PUBLISHED`/`ACTIVE` events; schedules future transitions or immediately transitions past-due events |

- Scheduling is deferred to `AFTER_COMMIT` so it only fires after the DB transaction commits (same pattern as Kafka publishers).
- Status guards inside `activateEvent`/`completeEvent`/`completeEventDirectly` make scheduled tasks idempotent — a no-op if the event was already cancelled or manually completed.
- `cancelScheduledTasks()` cancels the `ScheduledFuture` in the in-memory `ConcurrentHashMap` and removes it from the map.
- `recoverOnStartup()` uses a single query (`findByStatusIn(List.of(PUBLISHED, ACTIVE))`) and branches in-memory based on date comparisons.

### Manual Transitions

| Endpoint | Action |
|----------|--------|
| `DELETE /api/v1/events/{eventId}` | Cancels the event (must be `PUBLISHED`, >24h before start) |

## Event Management Flows

### Create Event
`POST /api/v1/events` — requires `X-User-Id` header
1. Builds `Event` entity with full venue layout (sections, zones, seats) via `EventFrontendMapper`
2. Persists to PostgreSQL
3. Indexes the event in Elasticsearch for search
4. Publishes `AuditEvent` and `EventCreatedEvent` via Kafka

### Search Events
`GET /api/v1/events/search?title=&organization=&category=`

Two implementations (selected at startup via `Searchbean` config):

| Implementation | Engine | Strategy |
|---------------|--------|----------|
| `ESSearchService` (active) | Elasticsearch | `MatchQuery` with fuzziness on `BoolQuery` — matches title, category, and organization |
| `PostgresSearchService` (fallback) | PostgreSQL | Native `pg_trgm` similarity query via `EventRepository.searchBy()` |

Both return a `Page<EventCardResponse>`.

### Cancel Event
`DELETE /api/v1/events/{eventId}` — requires `X-User-Id` header
1. Validates the event is `PUBLISHED` and cancellation is >24h before `startDate`
2. Sets status to `CANCELLED`
3. Removes from Elasticsearch index
4. Publishes `AuditEvent` and `EventCancelledEvent` via Kafka

### Get Event Layout
`GET /api/v1/events/{id}`
1. Fetches event with all sections and seats
2. For `SEAT`-model events, resolves currently active `SeatLock` records to mark locked seats
3. Returns full layout with locked seat IDs

## Lock & Reservation System

The lock system provides temporary reservations for both zone-based and seat-based events. All locking uses **pessimistic write locks** (`PESSIMISTIC_WRITE`) on the relevant DB rows to prevent race conditions.

### Seat Locks
- `acquireSeatLocks(eventId, LockSeatsRequest)` — locks specific seats
- Fails if any seat is already `BOOKED` or has an active `SeatLock`
- Uses `PESSIMISTIC_WRITE` on `Seat` rows
- Returns `LockSeatsResponse` with locked seat IDs and expiry

### Zone Locks
- `acquireZoneLock(eventId, LockZoneRequest)` — reserves quantity of capacity in a zone
- Uses `PESSIMISTIC_WRITE` on the `Section` row to atomically check/decrement `remainingCapacity`
- Throws `ZoneCapacityExceededException` if insufficient capacity
- Returns `LockZoneResponse` with zone ID and quantity

### Confirm & Release

| Action | Method | Description |
|--------|--------|-------------|
| Confirm | `confirm(reservationId)` | Confirms the reservation — books seats (sets `SeatStatus.BOOKED`) for seat locks, deletes all lock records, throws `LockExpiredException` if no locks found |
| Release | `release(reservationId)` | Releases all locks for a reservation — deletes lock records, restores `remainingCapacity` for zone locks |

Both confirm and release are idempotent-safe: if the reservation has already been processed, subsequent calls are handled gracefully.

### Lock Expiry
- Configurable TTL via `app.lock.ttl` (default 10 minutes)
- `EventStatusJobs` runs every 30 seconds, deleting up to 500 expired `SeatLock` and `ZoneLock` records per batch

## Event Publishing

All events are published via Kafka after the local transaction commits (`@TransactionalEventListener(phase = AFTER_COMMIT)`):

| Event | Kafka Key | Topic | Trigger |
|-------|-----------|-------|---------|
| `EventCreatedEvent` | `eventId` | `EVENT_CREATED` | Event created |
| `EventActivatedEvent` | `eventId` | `EVENT_ACTIVATED` | Event transitions to ACTIVE |
| `EventCompletedEvent` | `eventId` | `EVENT_COMPLETED` | Event transitions to COMPLETED |
| `EventPayoutReleaseEvent` | `eventId` | `EVENT_PAYOUT_RELEASED` | Event completes (triggers payout release for organizers) |
| `EventCancelledEvent` | `eventId` | `EVENT_CANCELLED` | Event cancelled |
| `AuditEvent` | `madeById` | `AUDIT_EVENT` | Event created, cancelled; section updated; seat status changed |

## Scheduled Jobs

| Job | Schedule | Description |
|-----|----------|-------------|
| `expireSeatLocks` | Every 30s | Deletes up to 500 expired `SeatLock` records |
| `expireZoneLocks` | Every 30s | Deletes up to 500 expired `ZoneLock` records |

## Private API (Inter-service)

**Base path:** `/api/v1/private/events` — `@Hidden` from public Swagger docs. Called by `reservation-service`.

| Endpoint | Description |
|----------|-------------|
| `POST /{eventId}/locks/seats` | Acquire temporary locks on specific seats |
| `POST /{eventId}/locks/zones` | Acquire temporary lock on a zone (capacity-based) |
| `POST /{eventId}/confirm` | Confirm a reservation (seat or zone) |
| `POST /{eventId}/release` | Release a reservation |

## Search Architecture

```java
@Component
public class Searchbean {
    @Bean
    public SearchService eventSearchService(ESSearchService esSearchService) {
        return esSearchService;
    }
}
```

- **Active:** Elasticsearch with `MatchQuery` (fuzziness enabled) on `BoolQuery` across title, category, and organization fields.
- **Fallback:** Postgres `pg_trgm` similarity search (swap the `@Bean` method to `PostgresSearchService`).
- Events are indexed on creation (`indexEvent`) and removed on cancellation (`deleteFromIndex`).

## Project Structure

```
src/main/java/org/ticketsouq/eventservice/
├── EventServiceApplication.java       # Entry point (@EnableScheduling, @EnableKafka, @EnableFeignClients, @EnableJpaAuditing)
├── Client/
│   └── UserServiceClient.java         # Feign client to user-service (org name resolution)
├── Config/
│   └── Searchbean.java                # Search implementation selector (ES vs Postgres)
├── Controller/
│   ├── EventController.java           # /api/v1/events/**
│   └── LockPrivateController.java     # /api/v1/private/events/** (@Hidden)
├── dto/
│   ├── ConfirmRequest/Response.java
│   ├── CreateSectionRequest.java
│   ├── EventSearchRequest.java
│   ├── LockSeatsRequest/Response.java
│   ├── LockZoneRequest/Response.java
│   ├── ReleaseRequest/Response.java
│   ├── SeatResponse.java
│   ├── SectionResponse.java
│   ├── UpdateSeatStatusRequest.java
│   ├── UpdateSectionRequest.java
│   ├── ZoneStatusResponse.java
│   └── FrontendMap/                   # Layout-aware DTOs + mapper
│       ├── CreateEventWithLayoutRequest.java
│       ├── EventCardResponse.java
│       ├── EventFrontendMapper.java
│       └── EventLayoutResponse.java
├── event/
│   └── EventEventPublisher.java       # Kafka event emitter (6 events)
├── jobs/
│   ├── EventStatusJobs.java           # @Scheduled lock expiry (30s)
│   └── EventStatusScheduler.java      # Programmatic TaskScheduler for event transitions
├── model/
│   ├── Event.java
│   ├── EventCategory.java
│   ├── Seat.java
│   ├── SeatLock.java
│   ├── Section.java
│   ├── ZoneLock.java
│   └── enums/
│       ├── BookingModel.java          # ZONE / SEAT / MIXED
│       ├── EventStatus.java           # PUBLISHED / ACTIVE / CANCELLED / COMPLETED
│       └── SeatStatus.java            # AVAILABLE / BOOKED_ORGANIZER / BOOKED
├── repository/
│   ├── ElasticsearchEventRepository.java
│   ├── EventCategoryRepository.java
│   ├── EventRepository.java
│   ├── SeatLockRepository.java
│   ├── SeatRepository.java
│   ├── SectionRepository.java
│   └── ZoneLockRepository.java
└── service/
    ├── EventService.java              # Event CRUD + status transitions
    ├── LockService.java               # Seat + zone lock/confirm/release
    ├── SeatService.java               # Organizer seat status management
    ├── SectionService.java            # Section CRUD
    └── Search/
        ├── ESSearchService.java       # Elasticsearch search implementation
        ├── EventIndex.java            # ES document mapping
        ├── PostgresSearchService.java # pg_trgm search fallback
        └── SearchService.java         # Search interface
```

## Running Locally

```bash
# Start infrastructure
docker compose up -d postgres elasticsearch kafka prometheus tempo loki

# Start config-server first
cd config-server && mvn spring-boot:run

# Start discovery-server
cd discovery-server && mvn spring-boot:run

# Start event-service
cd event-service && mvn spring-boot:run

# Start user-service (needed for org name resolution)
cd user-service && mvn spring-boot:run
```

The service registers with Eureka on a random port (`server.port: 0`) and is accessible through the API Gateway at `http://localhost:8080/api/v1/events/**`.
