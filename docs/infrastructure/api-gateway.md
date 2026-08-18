# API Gateway

Single entry point for the TicketSouq microservices platform. Handles authentication, token refresh, rate limiting, security headers, and request routing to backend services.

---

## Technologies

| Technology | Purpose |
|------------|---------|
| Spring Boot 4.0.3 / Spring Cloud 2025.1.1 | Runtime framework |
| Spring Security 7 | JWT auth, filter chain, method-level security |
| Spring Cloud Config | Centralized configuration from `config-server` |
| Eureka Discovery | Service registration and load-balanced routing via `RoutesConfig` |
| Bucket4j + Caffeine | In-memory token-bucket rate limiting |
| JJWT | JWT creation and signature verification (HMAC-SHA256) |
| PostgreSQL | Persistent auth credentials and refresh token sessions |
| Redis | Access token JTI blacklist, active session tracking (Lua scripts), OTP storage |
| Kafka | Async event publishing via the outbox pattern (`ticketsouq-outbox` module) |
| OpenFeign | Inter-service HTTP calls to `user-service` |
| Lombok | Boilerplate reduction |

---

## Dependencies

| Service | Purpose |
|---------|---------|
| `config-server` | Serves config at startup (port 8888) |
| `discovery-server` | Service registry (Eureka, port 8761) |
| `user-service` | User identity, profile, org membership checks, and bulk member generation |

Infrastructure required: PostgreSQL (per-service DB), Redis (token blacklist + session tracking), Kafka (async events), Prometheus + Loki + Tempo (observability).

---

## Auth Flows

All flows are in `AuthService` and `AuthTokenService`.

### Register
`POST /api/v1/auth/register`
1. Validates email uniqueness
2. Creates `AuthCredential` with BCrypt-hashed password (role `CUSTOMER`, or `ORG_HEAD` if an organization name is provided — then locked pending admin approval)
3. Calls `user-service` to create the user profile
4. Emits `EmailVerificationEvent` via the outbox

### Login
`POST /api/v1/auth/login`
1. Looks up `AuthCredential` by email
2. Checks `assertLoginAllowed()` — locked? inactive? unverified? banned org?
3. Verifies password against BCrypt hash
4. Tracks failed attempts (locks account after 5 failures for 2 minutes)
5. Generates access token (JWT, 5min TTL) + refresh token (DB row, 7d TTL)
6. Stores JTI in Redis for server-side validation

### Token Refresh (manual endpoint)
Auto-refresh inside `JwtAuthenticationFilter` is currently **disabled** (the refresh logic is commented out in the filter). Refresh is exposed as an explicit endpoint:

`POST /api/v1/auth/refresh` — requires `X-Refresh-Token` header (`AuthController.java:43-47`)
1. Validates the old refresh token and marks it revoked
2. Creates a new refresh token with a fresh `sessionId`
3. Returns a new access + refresh token pair

The `JwtAuthenticationFilter` (`JwtAuthenticationFilter.java:30-31`) only checks for the **`Authorization`** header (access token); it does not read or refresh via the `X-Refresh-Token` header:

| Scenario | Behavior |
|----------|----------|
| Valid access token | Authenticated |
| Invalid/expired access token | Anonymous (fails through to permitAll / downstream auth) |
| No headers | Anonymous |

### Logout
`POST /api/v1/auth/logout` — requires authentication
1. Extracts access token, deletes refresh token row, removes JTI from Redis

`POST /api/v1/auth/logout-all` — requires authentication
1. Deletes ALL refresh tokens for the user, removes all JTIs from Redis

### Password Change
`PUT /api/v1/auth/password` — requires authentication
1. Verifies current password against BCrypt hash
2. Updates the hash locally and publishes `PasswordChangedEvent` via the outbox (no `user-service` call)
3. Invalidates all sessions (forces re-login everywhere)

### Password Reset
1. `GET /api/v1/auth/password-forgot?email=` — generates a 6-digit OTP (stored in Redis) and sends it by email
2. `POST /api/v1/auth/password-forgot` — validates the OTP, updates hash, invalidates all sessions

### Email Verification
1. `GET /api/v1/auth/email-varification?email=` — generates a 6-digit OTP (stored in Redis) and sends it by email
2. `POST /api/v1/auth/email-varification` — validates the OTP, marks `isVerified = true`

### Org Account Generation
`POST /api/v1/auth/org/generate-accounts` — requires `ORG_HEAD` role
1. Creates N `ORG_Agent` and M `ORG_Consumer` accounts with random passwords
2. Calls `user-service` to register members
3. Emits `AccountsGeneratedEvent` via the outbox

---

## Security Architecture

### Filter Chain (in order)

| # | Filter | Responsibility |
|---|--------|----------------|
| 1 | `SecurityHeadersFilter` | Sets HSTS, CSP, X-Frame-Options, Permissions-Policy, COOP, CORP, X-Content-Type-Options, Referrer-Policy headers on every response |
| 2 | `RateLimitFilter` | Token-bucket per IP, configurable paths, returns `X-RateLimit-*` headers + 429 |
| 3 | `JwtAuthenticationFilter` | Extracts `Authorization` header, validates/sets auth context (auto-refresh disabled) |
| 4 | `HeaderForwardingFilter` | Injects `X-User-Id` header from security context into proxied requests |
| 5 | Spring Security's `UsernamePasswordAuthenticationFilter` | Standard security filter (form/login auth) |

Order is wired in `SecurityConfig.java:78-81` (`addFilterBefore` securityHeaders → rateLimit → jwt; `addFilterAfter` headerForwarding). Additionally, `HttpRequestLoggingFilter` wraps the entire chain to log method, URI, status, and duration.

### URL Access Rules

Defined in `SecurityRulesConfig`.

### Auth Credential vs User Service

- `AuthCredential` (local PostgreSQL) is the authority for authentication — email, password hash, role, locked/active/verified flags
- `user-service` is called for: registration, org membership checks, and member generation
- This keeps auth working even if `user-service` is temporarily down

### Token Types

| Type | Purpose | TTL |
|------|---------|-----|
| `ACCESS` | API authentication, stored in Redis as JTI | 5 min |
| `REFRESH` | Session identifier, stored in PostgreSQL row | 7 days |
| Email verification OTP | 6-digit code stored in Redis under `auth:otp:EMAIL:{otp}` | 5 min |
| Password reset OTP | 6-digit code stored in Redis under `auth:otp:PASSWORD:{otp}` | 5 min |

### Token Validation

- `parseToken()` — verifies JWT signature only (no expiry check)
- `isAccessTokenValid()` — checks type=ACCESS + JTI exists in Redis (Redis TTL enforces expiry)
- OTPs — looked up in Redis by code; deleted after use (one-time) via `validateEmailOtp()` / `validatePasswordResetOtp()`

### Refresh Token Rotation & Theft Detection

When a refresh token is used:
1. The old session row is locked with `PESSIMISTIC_WRITE`
2. If already revoked → token theft detected → ALL sessions for that user are revoked
3. Old row marked revoked, new row created with fresh `sessionId`
4. New access + refresh tokens issued

---

## Service Routing

Defined in `RoutesConfig`. The gateway routes `/api/v1/{service}/**` to the corresponding Eureka service using load-balanced URIs:

| Path Prefix | Target Service |
|-------------|----------------|
| `/api/v1/user/**` | `user-service` |
| `/api/v1/analytics/**` | `analytics-service` |
| `/api/v1/audit/**` | `audit-service` |
| `/api/v1/event/**` | `event-service` |
| `/api/v1/notification/**` | `notification-service` |
| `/api/v1/payment/**` | `payment-service` |
| `/api/v1/reservation/**` | `reservation-service` |
| `/api/v1/ticket/**` | `ticket-service` |
| `/api/v1/venue/**` | `venue-service` |

The prefix is derived from the service name by stripping `-service` (`RoutesConfig.java:58-64`). `POST /uploads/posters/**` is also routed to `event-service` for poster files, and `/eureka/**` is proxied to the discovery server. There is **no** `availability-locking-service`.

Each service also exposes aggregated OpenAPI docs at `/aggregate/{service}/v3/api-docs`.

---

## Rate Limiting

Configured in `config-repo/api-gateway/api-gateway.yaml:37-43` (served by `config-server`), not in `application.yaml`:

```yaml
rate-limit:
  paths: /**
  capacity: ${RATE_LIMIT:20}
  refill: ${RATE_LIMIT:20}
  refill-period: 1m
  allowed-origins:
    - http://${frontend.host}:${frontend.port}
```

- `capacity`/`refill` default to 20 but are overridden to `RATE_LIMIT: 2000` in docker-compose.yml.
- `allowed-origins` feeds Spring Security's CORS config (`SecurityConfig.corsConfigurationSource()`).
- Token-bucket with interval refill (all tokens at once per window, no bursting)
- Caffeine cache per IP, evicted after 2min idle, max 100k entries
- Respects `X-Forwarded-For` / `X-Real-IP` headers behind proxies
- Returns `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers

---

## Event Publishing

All events are published via the **outbox pattern** (`ticketsouq-outbox` module — `OutboxWriter` writes into the `ticket_souq_outbox` table in the same transaction; `OutboxRelay` polls and forwards to Kafka). There is **no** `AuthEventPublisher`/`@TransactionalEventListener` in this service:

| Event | Topic | Trigger |
|-------|-------|---------|
| `EmailVerificationEvent` | `user.email-verification` | Register / resend verification |
| `PasswordResetEvent` | `user.password-reset` | Password forgot request |
| `PasswordChangedEvent` | `user.password-change` | Password changed |
| `AuditEvent` | `audit.event` | Register, deactivate, unlock org, org member actions |
| `AccountsGeneratedEvent` | `accounts.generated` | Org account generation |

Outbox writes in `AuthService`: `EmailVerificationEvent` (:440), `PasswordResetEvent` (:197), `PasswordChangedEvent` (:244), `AccountsGeneratedEvent` (:317), `AuditEvent` (:449, :453).

---

## Scheduled Jobs

| Job | Cron | Description |
|-----|------|-------------|
| `RedisTokenSessionCleanUpJob` | Every 2 hours | Removes stale JTIs from Redis user session sets |
| `RefreshTokenCleanupJob` | Daily at midnight | Deletes revoked/expired refresh token rows |
| `UnlockUsers` | Every minute | Unlocks accounts whose lock period has expired |

---

## Inter-service Endpoint (Org Unlock)

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/auth/unlock-org` | Unlocks an org head account — called by `user-service` via Feign `AuthServiceClient` (`@PostMapping("/unlock-org")` under base path `/api/v1/auth`). There is **no** `/private` prefix; the handler lives in `AuthController.java:136-137`. |

---

## API Headers Reference

When calling the gateway as an authenticated client:

```
Authorization: <access_token>
```

Token refresh is done explicitly via `POST /api/v1/auth/refresh`:

```
X-Refresh-Token: <refresh_token>
```

On successful refresh, the response body contains the new token pair (`AuthResponse`).

---

## Key Notes

- **No auth event publisher bean** — all events flow through the outbox (`ticketsouq-outbox`), never through `@TransactionalEventListener`.
- **Refresh is explicit, not transparent** — `JwtAuthenticationFilter` ignores `X-Refresh-Token`; clients must call `POST /api/v1/auth/refresh` when the access token expires.
- **OTP tokens are one-time** — looked up in Redis by code and deleted after use.
- **Redis enforces access-token expiry** — the JWT signature has no expiry check; validity is JTI existence + Redis TTL.
- **Refresh token rotation detects theft** — reuse of a revoked token revokes all sessions for that user.

---

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
