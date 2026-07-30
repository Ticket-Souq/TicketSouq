# Password Reset — OTP-Based Flow

**Actors:** Client → API Gateway (OTP generation + Redis storage) → Kafka (event) → Notification Service (email with OTP) → Client (submits OTP + new password) → API Gateway (validates, hashes, persists)

---

## Sequence Diagram

```mermaid
sequenceDiagram
  participant Client
  participant AuthCtrl as "AuthController (api-gateway)"
  participant AuthSvc as "AuthService (api-gateway)"
  participant AuthToken as "AuthTokenService"
  participant Redis
  participant authDB as "auth-db (Postgres)"
  participant Kafka
  participant NotifConsumer as "NotificationEventConsumer"
  participant NotifSvc as "NotificationServiceImpl"
  participant notifDB as "notification-db (Postgres)"

  Note over Client,notifDB: ================ PHASE 1: FORGOT PASSWORD (REQUEST OTP) ================

  Client->>AuthCtrl: GET /api/v1/auth/password-forgot?email=user@example.com
  %% Source: api-gateway/.../controller/AuthController.java:82-86

  AuthCtrl->>AuthSvc: triggerPasswordReset(email)
  %% Source: api-gateway/.../controller/AuthController.java:84

  AuthSvc->>authDB: Find AuthCredential by email
  %% Source: api-gateway/.../service/AuthService.java:182

  AuthSvc->>AuthToken: generatePasswordResetOtp(userId)
  %% Source: api-gateway/.../service/AuthService.java:183

  AuthToken->>AuthToken: Generate 6-digit OTP
  %% Source: api-gateway/.../service/AuthTokenService.java:262-266

  AuthToken->>Redis: SET PASSWORD:{otp} → userId (TTL)
  %% Source: api-gateway/.../service/AuthTokenService.java:265

  AuthToken-->>AuthSvc: return otp ("123456")

  AuthSvc->>AuthSvc: Publish PasswordResetEvent (Spring event)
  %% Source: api-gateway/.../service/AuthService.java:184

  AuthCtrl-->>Client: 200 OK

  Note over Client,notifDB: ================ PHASE 2: ASYNC — SEND EMAIL ================

  AuthSvc->>Kafka: PasswordResetEvent (topic: user.password-reset)\n[AFTER_COMMIT]
  %% Source: api-gateway/.../event/AuthEventPublisher.java:31-35

  Kafka->>NotifConsumer: Consume PasswordResetEvent
  %% Source: notification-service/.../event/NotificationEventConsumer.java:36-39

  NotifConsumer->>NotifSvc: handlePasswordReset(event)
  %% Source: notification-service/.../event/NotificationEventConsumer.java:38

  NotifSvc->>notifDB: Look up UserEmailProjection by userId
  %% Source: notification-service/.../service/impl/NotificationServiceImpl.java:90-91

  NotifSvc->>notifDB: INSERT EmailJob\n(template: email/password-reset, var: resetUrl = otp)
  %% Source: notification-service/.../service/impl/NotificationServiceImpl.java:99-104

  Note over notifDB: Email sent to user with OTP

  Note over Client,notifDB: ================ PHASE 3: RESET PASSWORD (SUBMIT OTP) ================

  Client->>AuthCtrl: POST /api/v1/auth/password-forgot\n{ otp: "123456", newPassword: "..." }
  %% Source: api-gateway/.../controller/AuthController.java:88-92

  AuthCtrl->>AuthSvc: resetPassword(req)
  %% Source: api-gateway/.../controller/AuthController.java:90

  AuthSvc->>AuthToken: validatePasswordResetOtp(otp)
  %% Source: api-gateway/.../service/AuthService.java:197

  AuthToken->>Redis: GET PASSWORD:123456
  %% Source: api-gateway/.../service/AuthTokenService.java:277

  Redis-->>AuthToken: userId (or null)

  alt OTP not found or expired
    AuthToken-->>AuthSvc: throw "Invalid or expired reset OTP"
    AuthSvc-->>Client: 400 BAD REQUEST
  else OTP valid
    AuthToken->>Redis: DELETE PASSWORD:123456 (one-time use)
    %% Source: api-gateway/.../service/AuthTokenService.java:281

    AuthToken-->>AuthSvc: return userId

    AuthSvc->>authDB: Find AuthCredential by userId
    %% Source: api-gateway/.../service/AuthService.java:198

    AuthSvc->>AuthSvc: assertLoginAllowed(credential)
    %% Source: api-gateway/.../service/AuthService.java:199

    AuthSvc->>AuthSvc: Hash new password (BCrypt)
    %% Source: api-gateway/.../service/AuthService.java:200

    AuthSvc->>authDB: UPDATE password_hash
    %% Source: api-gateway/.../service/AuthService.java:201

    AuthSvc->>AuthSvc: logoutFromAllDevices(userId)\ninvalidates all refresh tokens & JTIs
    %% Source: api-gateway/.../service/AuthService.java:202

    AuthCtrl-->>Client: 200 OK
  end
```

---

## Detailed Step Breakdown

### Phase 1 — Request OTP (Forgot Password)

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 1a | Client calls GET `/password-forgot?email=` | `AuthController.java` | 82-86 |
| 1b | `AuthService.triggerPasswordReset()` | `AuthService.java` | 180-185 |
| 1c | `AuthTokenService.generatePasswordResetOtp()` — store in Redis | `AuthTokenService.java` | 262-267 |
| 1d | Publish `PasswordResetEvent` (Spring event) | `AuthService.java` | 184 |

### Phase 2 — Async Email Delivery

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 2a | `AuthEventPublisher` sends to Kafka `user.password-reset` | `AuthEventPublisher.java` | 31-35 |
| 2b | `NotificationEventConsumer` consumes | `NotificationEventConsumer.java` | 36-39 |
| 2c | `NotificationServiceImpl.handlePasswordReset()` | `NotificationServiceImpl.java` | 88-105 |
| 2d | Creates EmailJob with template `email/password-reset` | `NotificationServiceImpl.java` | 99-104 |

### Phase 3 — Submit OTP + New Password

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 3a | Client calls POST `/password-forgot` with OTP + new password | `AuthController.java` | 88-92 |
| 3b | `AuthTokenService.validatePasswordResetOtp()` — Redis lookup + delete | `AuthTokenService.java` | 275-283 |
| 3c | `assertLoginAllowed()` — checks verified, active, not locked | `AuthService.java` | 199 |
| 3d | Hash new password (BCrypt) and persist | `AuthService.java` | 200-201 |
| 3e | `logoutFromAllDevices()` — invalidate all sessions | `AuthService.java` | 202 |

---

## Redis OTP Key Structure

| Key Pattern | Value | TTL | Purpose |
|-------------|-------|-----|---------|
| `PASSWORD:{6-digit-otp}` | `userId` (UUID string) | `passwordResetExpiry` (ms) | One-time use, auto-expires |

**Evidence source:** `AuthTokenService.java:264-265`.

---

## Key Evidence Sources

| Component | File | Lines |
|-----------|------|-------|
| Forgot password endpoint (GET) | `AuthController.java` | 82-86 |
| Reset password endpoint (POST) | `AuthController.java` | 88-92 |
| Change password endpoint (PUT) | `AuthController.java` | 96-101 |
| AuthService.triggerPasswordReset | `AuthService.java` | 180-185 |
| AuthService.resetPassword | `AuthService.java` | 194-208 |
| AuthService.changePassword | `AuthService.java` | 219-230 |
| AuthTokenService.generatePasswordResetOtp | `AuthTokenService.java` | 262-267 |
| AuthTokenService.validatePasswordResetOtp | `AuthTokenService.java` | 275-283 |
| AuthEventPublisher → Kafka (password-reset) | `AuthEventPublisher.java` | 31-35 |
| Kafka topic constants | `TOPIC_NAMES.java` | 10-11 |
| Notification consumer for password-reset | `NotificationEventConsumer.java` | 36-39 |
| Notification consumer for password-change | `NotificationEventConsumer.java` | 41-44 |
| NotificationServiceImpl.handlePasswordReset | `NotificationServiceImpl.java` | 88-105 |
| NotificationServiceImpl.handlePasswordChanged | `NotificationServiceImpl.java` | 108-134 |
| PASSWORD_RESET template definition | `NotificationTemplate.java` | 18-24 |
| PASSWORD_CHANGED template definition | `NotificationTemplate.java` | 26-32 |
| Password-reset email HTML template | `password-reset.html` | 1-42 |
| Password-changed email HTML template | `password-changed.html` | 1-18 |
