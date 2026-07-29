# User Registration & Email Verification — Full Request Flow

**Actors:** Client → API Gateway (AuthCredential + password hashing) → User Service (profile + org) → Notification Service (email job)

---

## Sequence Diagram

```mermaid
sequenceDiagram
  participant Client
  participant GW as "API Gateway"
  participant authDB as "auth-db (Postgres)"
  participant US as "User Service"
  participant userDB as "user-db (Postgres)"
  participant Kafka
  participant NS as "Notification Service"
  participant notifDB as "notification-db (Postgres)"

  Note over Client,notifDB: ================ 1. REGISTRATION ================

  Client->>GW: POST /api/v1/auth/register\n{ email, password, name, organizationName? }

  GW->>GW: Check for duplicate email
  %% Source: api-gateway/.../service/AuthService.java:63

  GW->>GW: Build AuthCredential, hash password (BCrypt)
  %% Source: api-gateway/.../service/AuthService.java:382-398 (buildCredential)

  GW->>authDB: Save AuthCredential\n(userId, email, passwordHash, role)
  %% Source: api-gateway/.../service/AuthService.java:65

  GW->>US: Feign: registerUser(userId, name, email, orgName?)
  %% Source: api-gateway/.../client/UserServiceClient.java:13-14

  alt Has Organization Name (ORG_HEAD)
    US->>userDB: Create Organization (PENDING)
    %% Source: user-service/.../service/UserService.java:45-49
    US->>userDB: Create User profile
    %% Source: user-service/.../service/UserService.java:51-56
    US->>userDB: Create OrgMember (HEAD)
    %% Source: user-service/.../service/UserService.java:58-63
  else No Organization (CUSTOMER)
    US->>userDB: Create User profile only
    %% Source: user-service/.../service/UserService.java:70-76
  end

  GW->>GW: Generate email verification OTP (short-lived JWT)
  %% Source: api-gateway/.../service/AuthService.java:400-407 (sendVarificationNotification)

  GW->>Kafka: EmailVerificationEvent (topic: user.email-verification)
  %% Source: api-gateway/.../event/AuthEventPublisher.java:25-29

  GW->>Kafka: AuditEvent (topic: audit.event)
  %% Source: api-gateway/.../service/AuthService.java:68

  GW-->>Client: 201 CREATED

  Note over Client,notifDB: ================ 2. ASYNC: EMAIL VERIFICATION ================

  Kafka->>NS: Consume EmailVerificationEvent
  %% Source: notification-service/.../event/NotificationEventConsumer.java:46-48

  NS->>notifDB: Save in-app Notification (REGISTRATION type)
  NS->>notifDB: Create EmailJob (PENDING, template: email/registration.html)

  Note over NS: EmailScheduler picks up PENDING jobs,\nsends via SMTP (MockEmailSender)

  Note over Client,notifDB: ================ 3. USER VERIFIES EMAIL ================

  Client->>GW: POST /api/v1/auth/email-varification\n{ otp: "..." }

  GW->>GW: Validate OTP JWT, extract userId
  %% Source: api-gateway/.../service/AuthService.java:165-170

  GW->>authDB: Set isVerified = true
  %% Source: api-gateway/.../service/AuthService.java:168-169

  GW-->>Client: 200 OK
```

---

## Detailed Step Breakdown

### Step 1 — Registration

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 1a | Duplicate email check | `AuthService.java` | 63 |
| 1b | Hash password (BCrypt), build AuthCredential | `AuthService.java` | 64, 382-398 |
| 1c | Persist AuthCredential to auth-db | `AuthService.java` | 65 |
| 1d | Feign call: registerUser → User Service | `UserServiceClient.java` | 13-14 |
| 1e | (If org) Create Organization + User + OrgMember | `UserService.java` | 45-63 |
| 1f | (If no org) Create User profile only | `UserService.java` | 70-76 |
| 1g | Generate email verification OTP | `AuthService.java` | 400-407 |
| 1h | Publish EmailVerificationEvent → Kafka | `AuthEventPublisher.java` | 25-29 |
| 1i | Publish AuditEvent → Kafka | `AuthService.java` | 68 |

### Step 2 — Async Email Processing

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 2a | Consume EmailVerificationEvent | `NotificationEventConsumer.java` | 46-48 |
| 2b | Save in-app Notification + EmailJob | `NotificationServiceImpl.java` | — |

### Step 3 — Email Verification

| # | Action | File | Line(s) |
|---|--------|------|---------|
| 3a | Validate OTP JWT, extract userId | `AuthService.java` | 165-166 |
| 3b | Update AuthCredential: isVerified = true | `AuthService.java` | 167-169 |

---

## AuthCredential (auth-db) Schema

| Column | Type | Description |
|--------|------|-------------|
| user_id | UUID PK | Auto-generated, shared with User Service |
| email | VARCHAR UNIQUE | Login identifier |
| password_hash | VARCHAR | BCrypt hash |
| role | ENUM | CUSTOMER, ORG_HEAD, AGENT |
| is_active | BOOLEAN | Account active flag |
| is_verified | BOOLEAN | Email verified flag |
| failed_attempts | INT | Login failure counter |
| is_locked | BOOLEAN | Account lock flag |
| locked_until | TIMESTAMP | Lock expiration |

---

## Key Evidence Sources

| Step | File | Line(s) |
|------|------|---------|
| Register endpoint | `AuthController.java` | 30-34 |
| AuthService.register | `AuthService.java` | 62-69 |
| buildCredential | `AuthService.java` | 382-398 |
| sendVarificationNotification | `AuthService.java` | 400-407 |
| UserServiceClient Feign | `UserServiceClient.java` | 13-14 |
| UserService.register (org path) | `UserService.java` | 38-66 |
| UserService.register (customer path) | `UserService.java` | 69-77 |
| AuthEventPublisher → Kafka | `AuthEventPublisher.java` | 25-29 |
| EmailVerificationEvent consumed | `NotificationEventConsumer.java` | 46-48 |
| Verify email endpoint | `AuthService.java` | 164-170 |
