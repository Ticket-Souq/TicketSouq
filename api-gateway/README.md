# API Gateway

Single entry point for the TicketSouq microservices platform. Handles authentication, token refresh, rate limiting, security headers, and request routing to backend services.

## Tech Stack

- **Spring Boot 4.0.3** / **Spring Cloud 2025.1.1**
- **Spring Security 6** — JWT auth, filter chain, method-level security
- **Spring Cloud Config** — centralized configuration from `config-server`
- **Eureka Discovery** — service registration and load-balanced routing via `RoutesConfig`
- **Bucket4j + Caffeine** — in-memory token-bucket rate limiting
- **JJWT** — JWT creation and signature verification (HMAC-SHA256)
- **PostgreSQL** — persistent auth credentials and refresh token sessions
- **Redis** — access token JTI blacklist and active session tracking (Lua scripts)
- **Kafka** — async event publishing (email verification, password reset, audit, account generation)
- **OpenFeign** — inter-service HTTP calls to `user-service`
- **Lombok** — boilerplate reduction

## Dependencies

| Service | Purpose |
|---------|---------|
| `config-server` | Serves config at startup (port 8888) |
| `discovery-server` | Service registry (Eureka, port 8761) |
| `user-service` | User identity, profile, org membership checks, and bulk member generation |

Infrastructure required: PostgreSQL (per-service DB), Redis (token blacklist + session tracking), Kafka (async events), Prometheus + Loki + Tempo (observability).

## Auth Flows

All flows are in `AuthService` and `AuthTokenService`.

### Register
`POST /api/v1/auth/register`
1. Validates email uniqueness
2. Creates `AuthCredential` with BCrypt-hashed password (role `CUSTOMER` or `ORG_HEAD`)
3. Calls `user-service` to create the user profile
4. Emits `EmailVerificationEvent` via Kafka

### Login
`POST /api/v1/auth/login`
1. Looks up `AuthCredential` by email
2. Checks `assertLoginAllowed()` — locked? inactive? unverified? banned org?
3. Verifies password against BCrypt hash
4. Tracks failed attempts (locks account after 5 failures for 2 minutes)
5. Generates access token (JWT, 5min TTL) + refresh token (DB row, 7d TTL)
6. Stores JTI in Redis for server-side validation

### Token Refresh (auto)
The `JwtAuthenticationFilter` intercepts every request and checks for:
- **`Authentication` header** — the access token (plain, no `Bearer` prefix)
- **`refresh` header** — the refresh token

| Scenario | Behavior |
|----------|----------|
| Valid access token, with or without refresh | Authenticated, no refresh |
| Invalid/expired access token + valid refresh | Refresh: new tokens returned in response headers, then authenticated with new access token |
| Only refresh token (no access) | Refresh: same as above |
| Invalid/expired access token, no refresh | Anonymous |
| No headers | Anonymous |

When refresh succeeds, the response includes `Authentication` and `refresh` headers containing the new token pair. If refresh fails, the filter falls back to the original access token if still valid.

### Logout
`POST /api/v1/auth/logout` — requires authentication
1. Extracts access token, deletes refresh token row, removes JTI from Redis

`POST /api/v1/auth/logout-all` — requires authentication
1. Deletes ALL refresh tokens for the user, removes all JTIs from Redis

### Password Change
`PUT /api/v1/auth/password` — requires authentication
1. Verifies current password against BCrypt hash
2. Updates hash locally and calls `user-service` to update there too
3. Invalidates all sessions (forces re-login everywhere)

### Password Reset
1. `GET /api/v1/auth/password-forgot?email=` — sends email with short-lived JWT
2. `POST /api/v1/auth/password-forgot` — validates token, updates hash, invalidates all sessions

### Email Verification
1. `GET /api/v1/auth/email-varification?email=` — sends email with short-lived JWT
2. `POST /api/v1/auth/email-varification` — validates token, marks `isVerified = true`

### Org Account Generation
`POST /api/v1/auth/org/generate-accounts` — requires `ORG_HEAD` role
1. Creates N `ORG_Agent` and M `ORG_Consumer` accounts with random passwords
2. Calls `user-service` to register members
3. Emits `AccountsGeneratedEvent` via Kafka

## Security Architecture

### Filter Chain (in order)

| # | Filter | Responsibility |
|---|--------|----------------|
| 1 | `SecurityHeadersFilter` | Sets HSTS, CSP, X-Frame-Options, Permissions-Policy, COOP, CORP, X-Content-Type-Options, Referrer-Policy headers on every response |
| 2 | `RateLimitFilter` | Token-bucket per IP, configurable paths, returns `X-RateLimit-*` headers + 429 |
| 3 | `JwtAuthenticationFilter` | Extracts `Authentication` + `refresh` headers, validates/sets auth context, handles auto-refresh |
| 4 | Spring Security's `UsernamePasswordAuthenticationFilter` | Standard security filter (form/login auth) |
| 5 | `HeaderForwardingFilter` | Injects `X-User-Id` header from security context into proxied requests |

Additionally, `HttpRequestLoggingFilter` wraps the entire chain to log method, URI, status, and duration.

### URL Access Rules

Defined in `SecurityRulesConfig`

### Auth Credential vs User Service

- `AuthCredential` (local PostgreSQL) is the authority for authentication — email, password hash, role, locked/active/verified flags
- `user-service` is called for: registration, org membership checks, and member generation
- This keeps auth working even if `user-service` is temporarily down

### Token Types

| Type | Purpose | TTL |
|------|---------|-----|
| `ACCESS` | API authentication, stored in Redis as JTI | 5 min |
| `REFRESH` | Session identifier, stored in PostgreSQL row | 7 days |
| `EMAIL_VERIFICATION` | Email verification link | 5 min |
| `PASSWORD_RESET` | Password reset link | 5 min |

### Token Validation

- `parseToken()` — verifies JWT signature only (no expiry check)
- `isAccessTokenValid()` — checks type=ACCESS + JTI exists in Redis (Redis TTL enforces expiry)
- Email/password-reset tokens — expiry checked manually via `parseAndValidate()`

### Refresh Token Rotation & Theft Detection

When a refresh token is used:
1. The old session row is locked with `PESSIMISTIC_WRITE`
2. If already revoked → token theft detected → ALL sessions for that user are revoked
3. Old row marked revoked, new row created with fresh `sessionId`
4. New access + refresh tokens issued

## Service Routing

Defined in `RoutesConfig`. The gateway routes `/api/v1/{service}/**` to the corresponding Eureka service using load-balanced URIs:

| Path Prefix | Target Service |
|-------------|----------------|
| `/api/v1/user/**` | `user-service` |
| `/api/v1/analytics/**` | `analytics-service` |
| `/api/v1/audit/**` | `audit-service` |
| `/api/v1/availability-locking/**` | `availability-locking-service` |
| `/api/v1/event/**` | `event-service` |
| `/api/v1/notification/**` | `notification-service` |
| `/api/v1/payment/**` | `payment-service` |
| `/api/v1/reservation/**` | `reservation-service` |
| `/api/v1/ticket/**` | `ticket-service` |
| `/api/v1/venue/**` | `venue-service` |

Each service also exposes aggregated OpenAPI docs at `/aggregate/{service}/v3/api-docs`.

## Rate Limiting

Configured in `application.yaml`:

```yaml
rate-limit:
  paths: /**
  capacity: 20
  refill: 20
  refill-period: 1m
```

- Token-bucket with interval refill (all tokens at once per window, no bursting)
- Caffeine cache per IP, evicted after 2min idle, max 100k entries
- Respects `X-Forwarded-For` / `X-Real-IP` headers behind proxies
- Returns `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers

## Event Publishing

All events are published via Kafka after the local transaction commits (`@TransactionalEventListener(phase = AFTER_COMMIT)`):

| Event | Topic | Trigger |
|-------|-------|---------|
| `EmailVerificationEvent` | `user_email_verification` | Register / resend verification |
| `PasswordResetEvent` | `user_password_reset` | Password forgot request |
| `AuditEvent` | `audit_event` | Register, deactivate, unlock org |
| `AccountsGeneratedEvent` | `accounts_generated` | Org account generation |

## Scheduled Jobs

| Job | Cron | Description |
|-----|------|-------------|
| `RedisTokenSessionCleanUpJob` | Every 2 hours | Removes stale JTIs from Redis user session sets |
| `RefreshTokenCleanupJob` | Daily at midnight | Deletes revoked/expired refresh token rows |
| `UnlockUsers` | Every minute | Unlocks accounts whose lock period has expired |

## Private API (Inter-service)

**Base path:** `/api/v1/service/auth`

| Endpoint | Description                                                  |
|----------|--------------------------------------------------------------|
| `POST /unlock-org` | Unlocks an org head account (called by admin in his service) |

## Project Structure

```
src/main/java/org/ticketsouq/apigateway/
├── ApiGatewayApplication.java          # Entry point
├── client/
│   └── UserServiceClient.java          # Feign client to user-service
├── config/
│   ├── CustomUserDetails.java          # Spring Security principal
│   ├── RedisConfig.java                # Redis connection + template
│   ├── RoutesConfig.java               # Eureka-based routing
│   ├── SecurityConfig.java             # Security filter chain
│   ├── SecurityRule.java               # URL access rule record
│   ├── SecurityRulesConfig.java        # Bean for URL access rules
│   └── Filters/
│       ├── HeaderForwardingFilter.java # Injects X-User-Id
│       ├── HttpRequestLoggingFilter.java # Request/response logging
│       ├── JwtAuthenticationFilter.java # JWT auth + auto-refresh
│       ├── SecurityHeadersFilter.java  # Security response headers
│       └── RateLimit/
│           ├── RateLimitFilter.java
│           └── RateLimitProperties.java
├── controller/
│   ├── AuthController.java             # /api/v1/auth/**
│   └── AuthPrivateController.java      # /api/v1/service/auth/**
├── dto/
│   ├── AuthResponse.java
│   ├── ChangePasswordRequest.java
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   └── ResetPasswordRequest.java
├── event/
│   └── AuthEventPublisher.java         # Kafka event emitter
├── jobs/
│   └── CleaningJobs.java               # Scheduled token cleanup
├── model/
│   ├── AuthCredential.java
│   ├── RefreshToken.java
│   ├── Role.java
│   └── TokenType.java
├── repository/
│   ├── AccessTokenRepository.java      # Redis JTI store (Lua scripts)
│   ├── AuthCredentialRepository.java
│   └── RefreshTokenRepository.java
└── service/
    ├── AuthService.java                # Auth orchestration
    └── AuthTokenService.java           # JWT + token lifecycle
```

## Running Locally

```bash
# Start infrastructure
docker compose up -d redis kafka prometheus tempo loki

# Start config-server first
cd config-server && mvn spring-boot:run

# Start discovery-server
cd discovery-server && mvn spring-boot:run

# Start api-gateway
cd api-gateway && mvn spring-boot:run

# Start user-service (needed for register/verify/deactivate)
cd user-service && mvn spring-boot:run
```

The gateway will be available at `http://localhost:8080`.

## API Headers Reference

When calling the gateway as an authenticated client:

```
Authentication: <access_token>
refresh: <refresh_token>          # optional — only needed when access token is expired
```

On successful refresh, the response includes:

```
Authentication: <new_access_token>
refresh: <new_refresh_token>
```
