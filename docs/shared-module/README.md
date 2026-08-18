# Shared Module

A **non-executable** library JAR that contains code shared across all TicketSouq microservices. It is not a Spring Boot application (`pom.xml:45-50` disables repackaging). Every service that needs these types declares a dependency on `shared-module` in its own `pom.xml`.

---

## Package Overview

### DTOs — Inter-Service Request/Response Contracts

```
ApiGateway/dto/          CreateUserRequest, GenerateAccountRequest, GeneratedAccount, GenerateMembersRequest
EventService/dto/        LockSeatsRequest/Response, LockZoneRequest/Response, ReservationRequest, ReservationTicketDto, TicketReservationDto
PaymentService/dto/      SagaPaymentRequest/Response, RefundRequest
ReservationService/dto/  ConfirmRequest/Response, ReleaseRequest/Response
TicketService/dto/       CreateTicketRequest/Response, CancelTicketRequest/Response, TicketData
UserService/dto/         UserEmail
```

### Events — Kafka Message Payloads

```
ApiGateway/event/        AccountsGeneratedEvent, EmailVerificationEvent, PasswordChangedEvent, PasswordResetEvent
AuditService/events/     AuditEvent
EventService/events/     EventCreatedEvent, EventActivatedEvent, EventCompletedEvent, EventCancelledEvent,
                         EventPayoutReleaseEvent, BeginReservationEvent, OrganizerReservationCreatedEvent,
                         OrganizerReservationCancelledEvent
PaymentService/events/   PaymentSuccessEvent, PaymentFailedEvent, RefundRequestedEvent, RefundCompletedEvent
ReservationService/events/  SagaPaymentCommand, SagaPaymentReplyEvent, SagaPaymentCompensateCommand,
                         SagaTicketCommand, SagaTicketReplyEvent, SagaTicketCompensateCommand,
                         SagaLockConfirmCommand, SagaLockConfirmReplyEvent, SagaLockConfirmCompensateCommand,
                         ReservationCompletedEvent (9 saga events)
TicketService/events/    TicketIssuedEvent, TicketCancelledEvent
UserService/events/      OrganizationStatusChangedEvent
```

### Exceptions — Service-Specific Error Types

```
ApiGateway/exception/    EmailAlreadyExistsException, RateLimitExceededException, UserNotFoundException, GatewayExceptionHandler
AuditService/exception/  AuditLogNotFoundException
EventService/exception/  SeatAlreadyBookedException, SeatAlreadyLockedException, SeatNotInEventException,
                         LockExpiredException, ZoneCapacityExceededException, InvalidEventTypeException, LockExceptionHandler
GeneralExceptions/       BusinessException, BadRequestException, ConflictException, ForbiddenException,
                         ResourceNotFoundException, RemoteServiceUnavailableException, ErrorResponse, GlobalExceptionHandler
NotificationService/exception/  NotificationNotFoundException, EmailJobSerializationException,
                                UserEmailProjectionNotFoundException
PaymentService/exception/       PaymentException, PaymentExceptionHandler
ReservationService/exception/   ReservationExpiredException
```

### Constants

| Class | Content |
|-------|---------|
| `Constants/SERVICE_NAMES.java` | 13 service name constants (e.g., `API_GATEWAY`, `USER_SERVICE`, `CONFIG_SERVER`, `DISCOVERY_SERVER`) |
| `Constants/TOPIC_NAMES.java` | 27 Kafka topic constants (e.g., `user.email-verification`, `audit.event`, `saga.payment.command`) |

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

### Logging & Observability

```
logging/OutboxSqlTurboFilter.java        Redacts/suppresses outbox SQL in logs
observability/PyroscopeConfiguration.java  Profiling agent wiring (Pyroscope)
observability/JfrProfileExporter.java      JFR snapshot export for profiling
observability/PprofBuilder.java            pprof profile builder for Pyroscope
```

---

## Key Notes

- **No Spring Boot repackage** — the JAR is used as a plain library dependency (`pom.xml:45-50`).
- **GitHub Package Registry** is commented out in `pom.xml:70-76` — not currently used as a remote publisher.
- Every service's Kafka event consumer imports the event records from this module to deserialise messages.
- Feign clients in each service import DTOs from this module to ensure consistent API contracts across service boundaries.
