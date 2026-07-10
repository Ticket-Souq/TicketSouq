# API Gateway

Single entry point for the TicketSouq microservices platform. Handles authentication, rate limiting, security headers, and request routing.

## Tech Stack

- **Spring Boot 4.0.3** / **Spring Cloud 2025.1.1**
- **Spring Security 6** — JWT auth, filter chain, method-level security
- **Spring Cloud Config** — centralized configuration from `config-server`
- **Eureka Discovery** — service registration and load-balanced routing via `RoutesConfig`
- **Bucket4j + Caffeine** — in-memory token-bucket rate limiting
- **JJWT** — JWT creation and signature verification (HMAC-SHA256)
- **Lombok** — boilerplate reduction
- **MapStruct** — DTO/entity mapping

## Dependencies

| Service | Purpose |
|---------|---------|
| `config-server` | Serves config at startup (port 8888) |
| `discovery-server` | Service registry (Eureka, port 8761) |
| `user-service` | User identity, profile, and email verification |

Infrastructure required: PostgreSQL (per-service DB), Redis (token blacklist + session tracking), Kafka (async events), Prometheus + Loki + Tempo (observability).

## Auth Flows

All flows are in `AuthService` and `AuthTokenService`.

### Register
1. `POST /api/v1/auth/register` → validates input, calls `user-service` to create user
2. Creates a local `AuthCredential` with hashed password
3. Emits `EmailVerificationEvent` via Kafka
4. Returns tokens

### Login
1. `POST /api/v1/auth/login` → looks up `AuthCredential` by email
2. Checks `assertLoginAllowed()` — locked? inactive? unverified?
3. Verifies password hash against BCrypt
4. Generates access token (JWT, short-lived) + refresh token (DB row, rotated on use)
5. Stores JTI in Redis for server-side validation

### Refresh Token Rotation
1. `POST /api/v1/auth/refresh` — validates the refresh JWT, checks type=REFRESH
2. Looks up DB session row (PESSIMISTIC_WRITE lock)
3. If revoked → token theft detected → nukes ALL sessions for that user
4. Marks old row revoked, creates new row, returns new tokens

### Logout
1. `POST /api/v1/auth/logout` — extracts access token, deletes refresh row, removes JTI from Redis

### Password Change
1. `PUT /api/v1/auth/password` — verifies old password, validates new, updates hash in `AuthCredential` **and** calls `user-service` to update there too

### Password Reset
1. Sends email with short-lived JWT → validates token → updates hash

### Email Verification
1. Sends email with short-lived JWT → validates token → marks `isVerified = true`

## Security Architecture

### Filter Order (top to bottom)

| Order | Filter | Responsibility |
|-------|--------|----------------|
| 1 | `SecurityHeadersFilter` | Sets HSTS, CSP, X-Frame-Options, Permissions-Policy, COOP, CORP headers on every response |
| 2 | `RateLimitFilter` | Token-bucket per IP, configurable paths, returns `X-RateLimit-*` headers + 429 |
| 3 | `JwtAuthenticationFilter` | Extracts `Bearer` token, validates signature + JTI in Redis, populates `SecurityContext` |
| 4 | Spring Security's `UsernamePasswordAuthenticationFilter` | Form/login auth (if applicable) |

### Auth Credential vs User Service

- `AuthCredential` (local DB) is the authority for authentication — email, password hash, role, locked/active/verified flags
- `user-service` is only called for: registration, email verification, profile updates, and deactivation
- This keeps auth working even if `user-service` is temporarily down

### Token Validation

- `parseToken()` — verifies JWT signature only (no expiry check)
- `isAccessTokenValid()` — checks type=ACCESS + JTI exists in Redis (Redis TTL enforces expiry)
- Email/password-reset tokens → expiry checked manually via `parseAndValidate()`

### Rate Limiting

Configured in `application.yaml`:

```yaml
rate-limit:
  paths:
    - /api/v1/auth/**
  capacity: 20
  refill: 20
  refill-period: 1m
```

- Token-bucket with interval refill (all tokens at once per window, no bursting)
- Caffeine cache per IP, evicted after 2min idle, max 100k entries
- Respects `X-Forwarded-For` / `X-Real-IP` headers behind proxies
- Returns `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers

## Project Structure

```
src/main/java/org/ticketsouq/apigateway/
├── ApiGatewayApplication.java       # Entry point
├── config/
│   ├── CustomUserDetails.java       # Spring Security principal
│   ├── RedisConfig.java             # Redis connection + template
│   ├── RoutesConfig.java            # Eureka-based routing
│   ├── SecurityConfig.java          # Security filter chain
│   └── Filters/
│       ├── HttpRequestLoggingFilter.java
│       ├── JwtAuthenticationFilter.java
│       ├── SecurityHeadersFilter.java
│       └── RateLimit/
│           ├── RateLimitFilter.java
│           └── RateLimitProperties.java
├── client/
│   └── UserServiceClient.java       # Feign client to user-service
├── controller/
│   └── AuthController.java          # /api/v1/auth/**
├── dto/
│   ├── AuthResponse.java
│   ├── ChangePasswordRequest.java
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   └── ResetPasswordRequest.java
├── event/
│   └── AuthEventPublisher.java      # Kafka event emitter
├── jobs/
│   └── CleaningJobs.java            # Scheduled token cleanup
├── model/
│   ├── AuthCredential.java
│   ├── RefreshToken.java
│   ├── Role.java
│   └── TokenType.java
├── repository/
│   ├── AccessTokenRepository.java   # Redis JTI store
│   ├── AuthCredentialRepository.java
│   └── RefreshTokenRepository.java
└── service/
    ├── AuthService.java             # Auth orchestration
    └── AuthTokenService.java        # JWT + token lifecycle
```

## Configuration

Most properties come from `config-server` (`config-repo/`). Service-specific overrides:

| Key | Default | Description |
|-----|---------|-------------|
| `server.port` | `8080` | Gateway port |
| `jwt.secret` | — | HMAC key (min 32 bytes) |
| `jwt.access-token-expiry-ms` | `900000` (15min) | Access token TTL |
| `jwt.refresh-token-expiry-ms` | `604800000` (7d) | Refresh token TTL |
| `jwt.email-token-expiry-ms` | `900000` (15min) | Email verification TTL |
| `jwt.password-reset-expiry-ms` | `900000` (15min) | Password reset TTL |
| `rate-limit.capacity` | `20` | Token bucket max |
| `rate-limit.refill` | `20` | Tokens per window |
| `rate-limit.refill-period` | `1m` | Refill window |

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
