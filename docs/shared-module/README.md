# Shared Module

A **non-executable** library JAR that contains code shared across all TicketSouq microservices. It is not a Spring Boot application (`pom.xml:42-44` disables repackaging). Every service that needs these types declares a dependency on `shared-module` in its own `pom.xml`.

---

## Technologies

| Technology | Purpose | Evidence |
|------------|---------|----------|
| OpenFeign core | Feign DTOs for inter-service HTTP contracts | `pom.xml:16-18` — dependency `feign-core` |
| Spring Security Core | Security enums and constants | `pom.xml:20-22` — dependency `spring-security-core` |
| JJWT (io.jsonwebtoken) 0.12.6 | JWT token types for auth DTOs | `pom.xml:24-28` — dependency `jjwt-api` |
| Tomcat Embed Core | Servlet types used in exception handlers | `pom.xml:30-32` — dependency `tomcat-embed-core` |

---

## Package Overview

### DTOs — Inter-Service Request/Response Contracts

```
ApiGateway/dto/          CreateUserRequest, GenerateAccountRequest, GeneratedAccount, GenerateMembersRequest
EventService/dto/        LockSeatsRequest/Response, LockZoneRequest/Response, ReservationRequest, TicketReservationDto
PaymentService/dto/      SagaPaymentRequest/Response, RefundRequest
ReservationService/dto/  ConfirmRequest/Response, ReleaseRequest/Response
TicketService/dto/       CreateTicketRequest/Response, CancelTicketRequest/Response, TicketData
```

### Events — Kafka Message Payloads

```
ApiGateway/event/        AccountsGeneratedEvent, EmailVerificationEvent, PasswordChangedEvent, PasswordResetEvent
AuditService/events/     AuditEvent
EventService/events/     EventCreatedEvent, EventActivatedEvent, EventCompletedEvent, EventCancelledEvent,
                         EventPayoutReleaseEvent, BeginReservationEvent
PaymentService/events/   PaymentSuccessEvent, PaymentFailedEvent, RefundRequestedEvent, RefundCompletedEvent
ReservationService/events/  Saga*Command + Saga*ReplyEvent + Saga*CompensateCommand (8 events)
TicketService/events/    TicketIssuedEvent, TicketCancelledEvent
```

### Exceptions — Service-Specific Error Types

```
ApiGateway/exception/    EmailAlreadyExistsException, RateLimitExceededException, UserNotFoundException
AuditService/exception/  AuditLogNotFoundException
EventService/exception/  SeatAlreadyBookedException, SeatAlreadyLockedException, SeatNotInEventException,
                         LockExpiredException, ZoneCapacityExceededException, InvalidEventTypeException
GeneralExceptions/       BusinessException, BadRequestException, ConflictException, ForbiddenException,
                         ResourceNotFoundException, ErrorResponse, GlobalExceptionHandler
NotificationService/exception/  NotificationNotFoundException, EmailJobSerializationException,
                                UserEmailProjectionNotFoundException
PaymentService/exception/       PaymentException
ReservationService/exception/   ReservationExpiredException
```

### Constants

| Class | Content | Evidence |
|-------|---------|----------|
| `Constants/SERVICE_NAMES.java` | 13 service name constants (e.g., `API_GATEWAY`, `USER_SERVICE`, `CONFIG_SERVER`, `DISCOVERY_SERVER`) | `SERVICE_NAMES.java:7-19` |
| `Constants/TOPIC_NAMES.java` | 21 Kafka topic constants (e.g., `user.email-verification`, `audit.event`, `saga.payment.command`) | `TOPIC_NAMES.java:8-41` |

### Enums

```
ReservationService/enums/  ReservationStatus
```

### Utilities

```
utils/UUIDUtils.java       UUID parsing helpers
utils/LogUtils.java        Standardised log messages for event publish/consume lifecycle
```

### Validation

```
Validation/NullOrNotBlank.java          Custom annotation
Validation/NullOrNotBlankValidator.java  Its implementation
```

---

## Key Notes

- **No Spring Boot repackage** — the JAR is used as a plain library dependency (`pom.xml:42-44`).
- **GitHub Package Registry** is commented out in `pom.xml:65-71` — not currently used as a remote publisher.
- Every service's Kafka event consumer imports the event records from this module to deserialise messages.
- Feign clients in each service import DTOs from this module to ensure consistent API contracts across service boundaries.
