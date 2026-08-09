package org.ticketsouq.apigateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.apigateway.client.UserServiceClient;
import org.ticketsouq.apigateway.dto.*;
import org.ticketsouq.apigateway.metrics.GatewayMetrics;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.ticketsouq.apigateway.model.RefreshToken;
import org.ticketsouq.apigateway.model.Role;
import org.ticketsouq.apigateway.repository.AuthCredentialRepository;
import org.ticketsouq.outbox.service.OutboxWriter;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateAccountRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GeneratedAccount;
import org.ticketsouq.sharedmodule.ApiGateway.event.AccountsGeneratedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.EmailVerificationEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordChangedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordResetEvent;
import org.ticketsouq.sharedmodule.ApiGateway.exception.EmailAlreadyExistsException;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.sharedmodule.utils.UUIDUtils;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.ACCOUNTS_GENERATED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.AUDIT_EVENT;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.USER_EMAIL_VERIFICATION;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.USER_PASSWORD_CHANGE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.USER_PASSWORD_RESET;

import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthTokenService authTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserServiceClient userServiceClient;
    private final AuthCredentialRepository credentialRepository;
    private final OutboxWriter outboxWriter;
    private final PlatformTransactionManager transactionManager;
    private final GatewayMetrics gatewayMetrics;

    @Value("${app.generated-email-domain:ticketaty.com}")
    private String generatedEmailDomain;

    // ── REGISTER ──────────────────────────────────────────────────────────────

    /*
     * Registers a new user:
     * 1. Checks for duplicate email
     * 2. Builds a local AuthCredential with role=CUSTOMER (or ORG_HEAD if org name provided)
     * 3. Persists the credential (JPA auto-generates the userId)
     * 4. Creates the user in user-service via Feign with the generated userId
     * 5. Publishes an EmailVerificationEvent to trigger a verification email
     */
    @Transactional
    public void register(RegisterRequest req) {
        if (credentialRepository.existsByEmail(req.email())) throw new EmailAlreadyExistsException("Email already in use");
        AuthCredential credential = buildCredential(req);
        credentialRepository.save(credential);
        userServiceClient.registerUser(new CreateUserRequest(credential.getUserId(), req.name(), req.email(), req.OrganizationName()));
        sendVarificationNotification(credential);
        sendAuditEventWithNoReason("Register", credential.getUserId());
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────

    /*
     * Authenticates a user with email + password:
     * 1. Looks up credential by email
     * 2. Validates account state (verified, active, not locked)
     * 3. Verifies password against stored hash
     * 4. On bad password: increments failed-attempt counter, locks account at 5 failures, throws
     * 5. On success: resets failed-attempt counter (if had prior failures), issues access + refresh tokens
     */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        AuthCredential credential = getCredentialByEmail(req.email());
        assertLoginAllowed(credential);

        if (!passwordEncoder.matches(req.password(), credential.getPasswordHash())) handleFailedAttempt(credential);
        if (hasPriorFailures(credential)) resetFailedAttempts(credential);
        credential.setLastLogin(Instant.now());
        return buildAuthResponse(credential, authTokenService.createNewRefreshToken(credential.getUserId()));
    }

    // ── REFRESH ───────────────────────────────────────────────────────────────

    /*
     * Rotates a refresh token:
     * 1. Validates the old refresh token and marks it revoked
     * 2. Creates a new refresh token with a new sessionId
     * 3. Looks up the credential for the associated userId
     * 4. Returns a new access + refresh token pair
     * On reuse detection: revokes ALL sessions for that user (token theft protection)
     */
    public AuthResponse refresh(String refreshToken) {
        RefreshToken newToken = authTokenService.refresh(refreshToken);
        AuthCredential credential = getCredentialByUserId(newToken.getUserId());
        assertLoginAllowed(credential);
        return buildAuthResponse(credential, newToken);
    }

    private AuthResponse buildAuthResponse(AuthCredential credential, RefreshToken refreshToken) {
        UUID userId = credential.getUserId();
        List<String> roles = List.of(credential.getRole().name());
        String access = authTokenService.generateAccessToken(userId, credential.getEmail(), roles, refreshToken.getSessionId());
        String refresh = authTokenService.generateRefreshToken(userId, refreshToken.getSessionId());
        return new AuthResponse(access, refresh);
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────

    /*
     * Logs out the current device:
     * 1. Parses the access token to extract the session ID
     * 2. Deletes the refresh token row for that session
     * 3. Removes the access token's JTI from Redis
     * 4. Clears the security context
     */
    public void logout(String accessToken) {
        authTokenService.invalidateSession(accessToken);
        SecurityContextHolder.clearContext();
    }

    /*
     * Logs out ALL devices for the given user:
     * 1. Deletes all refresh tokens for this userId
     * 2. Removes all access token JTIs from Redis
     * 3. Clears the security context
     */
    public void logoutFromAllDevices(UUID userId) {
        authTokenService.invalidateAllSession(userId);
        SecurityContextHolder.clearContext();
    }

    // ── EMAIL VERIFY ──────────────────────────────────────────────────────────

    /*
     * Sends a verification email (idempotent):
     * 1. Looks up credential by email
     * 2. If already verified, returns early (no-op)
     * 3. Generates a short-lived email-verification JWT
     * 4. Publishes an EmailVerificationEvent (async consumer sends the email)
     */
    @Transactional
    public void triggerVerificationEmail(String email) {
        AuthCredential credential = getCredentialByEmail(email);
        if (credential.getIsVerified()) return;
        sendVarificationNotification(credential);
    }

    /*
     * Verifies email using the token:
     * 1. Validates the email-verification JWT → extracts userId
     * 2. Sets isVerified=true on the local credential
     * 3. Persists the change
     */
    @Transactional
    public void verifyEmail(String otp) {
        UUID userId = authTokenService.validateEmailOtp(otp);
        AuthCredential credential = getCredentialByUserId(userId);
        credential.setIsVerified(true);
        credentialRepository.save(credential);
    }

    // ── FORGOT / RESET PASSWORD ───────────────────────────────────────────────

    /*
     * Initiates password reset flow:
     * 1. Looks up credential by email
     * 2. Generates a short-lived password-reset JWT
     * 3. Publishes a PasswordResetEvent (async consumer sends the email)
     */
    @Transactional
    public void triggerPasswordReset(String email) {
        AuthCredential credential = getCredentialByEmail(email);
        String otp = authTokenService.generatePasswordResetOtp(credential.getUserId());
        outboxWriter.save(new PasswordResetEvent(UUID.randomUUID(), credential.getUserId(), email, otp),
            USER_PASSWORD_RESET, credential.getUserId().toString());
    }

    /*
     * Resets password using the token:
     * 1. Validates the password-reset JWT → extracts userId
     * 2. Looks up credential, checks account state (verified, active, not locked)
     * 3. Hashes the new password and persists locally
     * 4. Invalidates all active sessions (forces re-login everywhere)
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        try {
            UUID userId = authTokenService.validatePasswordResetOtp(req.otp());
            AuthCredential credential = getCredentialByUserId(userId);
            assertLoginAllowed(credential);
            credential.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            credentialRepository.save(credential);
            logoutFromAllDevices(userId);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException("Invalid or expired reset OTP", HttpStatus.BAD_REQUEST);
        }
    }

    // ── CHANGE PASSWORD ───────────────────────────────────────────────────────

    /*
     * Changes password for authenticated user:
     * 1. Looks up credential, checks account state
     * 2. Verifies current password against stored hash
     * 3. Hashes the new password and persists locally
     * 4. Invalidates all active sessions (forces re-login everywhere)
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        AuthCredential credential = getCredentialByUserId(userId);
        assertLoginAllowed(credential);

        if (!passwordEncoder.matches(req.currentPassword(), credential.getPasswordHash()))
            throw new BusinessException("Current password is incorrect", HttpStatus.UNAUTHORIZED);

        credential.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        credentialRepository.save(credential);
        logoutFromAllDevices(userId);
        outboxWriter.save(new PasswordChangedEvent(UUID.randomUUID(), userId),
            USER_PASSWORD_CHANGE, userId.toString());
    }

    // ── DEACTIVATE ACCOUNT ────────────────────────────────────────────────────

    /*
     * Deactivates the authenticated user's account:
     * 1. Looks up credential, checks account state
     * 2. Sets isActive=false locally and persists
     * 3. Invalidates all active sessions
     * 4. Clears the security context
     */
    @Transactional
    public void deactivateAccount(UUID userId) {
        AuthCredential credential = getCredentialByUserId(userId);
        assertLoginAllowed(credential);
        credential.setIsActive(false);
        credentialRepository.save(credential);
        logoutFromAllDevices(userId);
        SecurityContextHolder.clearContext();
        sendAuditEventWithNoReason("deActivate Account", credential.getUserId());
    }

    @Transactional
    public void deactivateEmployeeAccount(UUID userId) {
        AuthCredential credential = getCredentialByUserId(userId);
        credential.setIsActive(false);
        credentialRepository.save(credential);
        logoutFromAllDevices(userId);
        sendAuditEventWithNoReason("Organization head deActivate Account", credential.getUserId());
    }

    @Transactional
    public void reactivateEmployeeAccount(UUID userId) {
        AuthCredential credential = getCredentialByUserId(userId);
        credential.setIsActive(true);
        credentialRepository.save(credential);
        sendAuditEventWithNoReason("Organization head deActivate Account", credential.getUserId());
    }

    @Transactional
    public void unlockOrg(UUID orgHeadId) {
        AuthCredential credential = getCredentialByUserId(orgHeadId);
        credential.setLocked(false);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);
        sendAuditEventWithNoReason("Approve Organization Head Request", credential.getUserId());
    }

    @Transactional(readOnly = true)
    public List<OrgMemberWithStatus> getOrgMembers(UUID orgHeadUserId) {
        List<OrgMemberResponse> members = userServiceClient.getOrgMembers(orgHeadUserId);
        return members.stream().map(m -> {
            boolean active = credentialRepository.findByUserId(m.userId())
                .map(AuthCredential::getIsActive)
                .orElse(false);
            return new OrgMemberWithStatus(
                m.userId(), m.name(), m.email(), m.memberRole(),
                m.orgId(), m.organizationName(), active);
        }).toList();
    }

    @Transactional
    public List<GeneratedAccount> generateAccountsForOrg(UUID orgHeadUserId, GenerateAccountRequest req) {
        if (req.consumerCount()==0 && req.agentCount()==0) return List.of();
        List<GeneratedAccount> accounts = new ArrayList<>();
        List<GenerateMembersRequest.MemberToCreate> members = new ArrayList<>();

        for (int i = 0; i < req.agentCount(); i++) addMember(accounts,members,Role.ORG_Agent);
        for (int i = 0; i < req.consumerCount(); i++) addMember(accounts,members,Role.ORG_Consumer);

        userServiceClient.generateMembers(new GenerateMembersRequest(orgHeadUserId, members));
        outboxWriter.save(new AccountsGeneratedEvent(UUID.randomUUID(), orgHeadUserId,
            accounts.stream().map(a -> new AccountsGeneratedEvent.AccountInfo(UUIDUtils.parse(a.userId()), a.email(), a.password(), a.role())).toList()),
            ACCOUNTS_GENERATED, orgHeadUserId.toString());

        return accounts;
    }

    private void addMember(List<GeneratedAccount> accounts, List<GenerateMembersRequest.MemberToCreate> members , Role role ) {
        String prefix = role==Role.ORG_Agent? "agent_":"consumer_";
        String email = prefix + UUID.randomUUID().toString().substring(0, 8) + "@" + generatedEmailDomain;
        String rawPassword = generateRandomString(8);
        AuthCredential credential = buildGeneratedCredential(email, rawPassword, role);
        credentialRepository.save(credential);
        accounts.add(new GeneratedAccount(credential.getUserId().toString(), email, rawPassword, role.name()));
        members.add(new GenerateMembersRequest.MemberToCreate(credential.getUserId(), email, role.name()));
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private AuthCredential getCredentialByEmail(String email) {
        return credentialRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));
    }

    private AuthCredential getCredentialByUserId(UUID userId) {
        return credentialRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));
    }

    /*
     * Checks account state before allowing login or sensitive operations.
     * Rejects if: not verified, not active, or locked (with reason: bad attempts vs. org approval)
     */
    private void assertLoginAllowed(AuthCredential c) {
        if (!c.getIsVerified()) {
            gatewayMetrics.recordAuthFailure("not_verified");
            throw new BusinessException("Account is not verified", HttpStatus.UNAUTHORIZED);
        }

        if (!c.getIsActive()) {
            gatewayMetrics.recordAuthFailure("not_active");
            throw new BusinessException("Account is not active. Contact support.", HttpStatus.UNAUTHORIZED);
        }

        if (c.getLocked()) {
            boolean  lockedDueToFailedAttempts = c.getLockedUntil() != null && c.getLockedUntil().isAfter(Instant.now())
                && c.getLockedUntil().isBefore(Instant.now().plus(Duration.ofHours(1)));

            if (lockedDueToFailedAttempts) {
                LocalDateTime time = c.getLockedUntil().atZone(ZoneId.systemDefault()).toLocalDateTime();
                String failedLoginMessage = "Account is locked until " +
                    time.getHour() +":"+ time.getMinute()+
                    ". Because of multiple failed login attempt.";
                gatewayMetrics.recordAuthFailure("account_locked");
                throw new BusinessException(failedLoginMessage, HttpStatus.UNAUTHORIZED);
            }
            gatewayMetrics.recordAuthFailure("pending_org_approval");
            throw new BusinessException("Waiting for Admin Approval for your Organization", HttpStatus.UNAUTHORIZED);
        }
        if (!(c.getRole().name().equals("ADMIN") || c.getRole().name().equals("CUSTOMER"))) {
            if (userServiceClient.isBelongToBannedOrg(c.getUserId())) {
                gatewayMetrics.recordAuthFailure("banned_org");
                throw new BusinessException("you belong to a banned organization you are not allowed to login", HttpStatus.UNAUTHORIZED);
            }
        }
    }


    private void handleFailedAttempt(AuthCredential c) {
        gatewayMetrics.recordAuthFailure("wrong_password");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            c.setFailedAttempts(c.getFailedAttempts() == null ? 1 : c.getFailedAttempts() + 1);
            if (c.getFailedAttempts() >= 5) {
                c.setLocked(true);
                c.setLockedUntil(Instant.now().plus(2, ChronoUnit.MINUTES));
            }
            credentialRepository.save(c);
            return null;
        });
        throw new BusinessException("Wrong password please try again", HttpStatus.UNAUTHORIZED);
    }

    private boolean hasPriorFailures(AuthCredential c) {
        return c.getFailedAttempts() != null && c.getFailedAttempts() > 0;
    }

    /*
     * Clears the failed-attempt counter and unlocks the account.
     * Called on successful login when there were prior failures.
     */
    private void resetFailedAttempts(AuthCredential c) {
        c.setFailedAttempts(0);
        c.setLocked(false);
        c.setLockedUntil(null);
        credentialRepository.save(c);
    }

    /*
     * Creates the user in user-service via Feign, then builds and returns
     * a local AuthCredential. If an organizationName was provided, the role
     * is set to ORG_HEAD and the account is locked pending admin approval.
     */
    private AuthCredential buildCredential(RegisterRequest req) {
        AuthCredential credential = AuthCredential.builder()
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(Role.CUSTOMER)
            .isActive(true)
            .isVerified(false)
            .locked(false)
            .build();

        if (req.OrganizationName() != null) {
            credential.setRole(Role.ORG_HEAD);
            credential.setLocked(true);
            credential.setLockedUntil(LocalDateTime.of(9999, 12, 31, 23, 59, 59).toInstant(ZoneOffset.UTC));
        }
        return credential;
    }

    private void sendVarificationNotification(AuthCredential credential) {
        outboxWriter.save(new EmailVerificationEvent(
            UUID.randomUUID(),
            credential.getUserId(),
            credential.getEmail(),
            authTokenService.generateEmailVerificationOtp(credential.getUserId())
        ), USER_EMAIL_VERIFICATION, credential.getUserId().toString());
    }

    private void sendAuditEvent(String action, UUID madeById, String reason) {
        outboxWriter.save(new AuditEvent(action, madeById, reason, Instant.now()), AUDIT_EVENT, madeById.toString());
    }

    private void sendAuditEventWithNoReason(String action, UUID madeById) {
        outboxWriter.save(new AuditEvent(action, madeById, "", Instant.now()), AUDIT_EVENT, madeById.toString());
    }

    private String generateRandomString(int length) {
        String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom RANDOM = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private AuthCredential buildGeneratedCredential(String email, String rawPassword, Role role) {
        return AuthCredential.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .isActive(true)
            .isVerified(true)
            .locked(false)
            .build();
    }
}
