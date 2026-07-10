package org.ticketsouq.apigateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.apigateway.client.UserServiceClient;
import org.ticketsouq.apigateway.dto.*;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.event.EmailVerificationEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordResetEvent;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.ticketsouq.apigateway.model.RefreshToken;
import org.ticketsouq.apigateway.model.Role;
import org.ticketsouq.apigateway.repository.AuthCredentialRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    // ── REGISTER ──────────────────────────────────────────────────────────────

    /*
     * Registers a new user:
     * 1. Creates the user in user-service via Feign (returns a UUID of that user object)
     * 2. Builds a local AuthCredential with role=CUSTOMER (or ORG_HEAD if org name provided)
     * 3. Persists the credential
     * 4. Publishes an EmailVerificationEvent to trigger a verification email
     */
    @Transactional
    public void register(RegisterRequest req) {
        AuthCredential credential = buildCredential(req);
        credentialRepository.save(credential);
        applicationEventPublisher.publishEvent(new EmailVerificationEvent(
            credential.getUserId(),
            credential.getEmail(),
            authTokenService.generateEmailVerificationToken(credential.getUserId())
        ));
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
    public void triggerVerificationEmail(String email) {
        AuthCredential credential = getCredentialByEmail(email);
        if (credential.getIsVerified()) return;
        String token = authTokenService.generateEmailVerificationToken(credential.getUserId());
        applicationEventPublisher.publishEvent(new EmailVerificationEvent(credential.getUserId(), email, token));
    }

    /*
     * Verifies email using the token:
     * 1. Validates the email-verification JWT → extracts userId
     * 2. Sets isVerified=true on the local credential
     * 3. Persists the change
     */
    @Transactional
    public void verifyEmail(String token) {
        UUID userId = authTokenService.validateEmailToken(token);
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
    public void triggerPasswordReset(String email) {
        AuthCredential credential = getCredentialByEmail(email);
        String token = authTokenService.generatePasswordResetToken(credential.getUserId());
        applicationEventPublisher.publishEvent(new PasswordResetEvent(credential.getUserId(), email, token));
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
            UUID userId = authTokenService.validatePasswordResetToken(req.token());
            AuthCredential credential = getCredentialByUserId(userId);
            assertLoginAllowed(credential);
            credential.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            credentialRepository.save(credential);
            logoutFromAllDevices(userId);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException("Invalid or expired reset token", HttpStatus.BAD_REQUEST);
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
        if (!c.getIsVerified())
            throw new BusinessException("Account is not verified", HttpStatus.UNAUTHORIZED);

        if (!c.getIsActive())
            throw new BusinessException("Account is not active. Contact support.", HttpStatus.UNAUTHORIZED);

        if (c.getLocked()) {
            if (hasPriorFailures(c)) {
                String FailedLoginMessage = "Account is locked until " +
                                            c.getLockedUntil().atZone(ZoneId.systemDefault()).toLocalDateTime() +
                                            ". Because of multiple failed login attempt.";
                throw new BusinessException(FailedLoginMessage, HttpStatus.UNAUTHORIZED);
            }
            throw new BusinessException("Waiting for Admin Approval for your Organization", HttpStatus.UNAUTHORIZED);
        }
    }

    /*
     * Increments the failed-login counter and locks the account when it reaches 5.
     * Always throws after recording the attempt.
     */
    private void handleFailedAttempt(AuthCredential c) {
        c.setFailedAttempts(c.getFailedAttempts() == null ? 1 : c.getFailedAttempts() + 1);
        if (c.getFailedAttempts() >= 5) {
            c.setLocked(true);
            c.setLockedUntil(Instant.now().plus(2, ChronoUnit.MINUTES));
        }
        credentialRepository.save(c);
        throw new BusinessException("Bad credentials", HttpStatus.UNAUTHORIZED);
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
     * a local AuthCredential. If an OrganizationName was provided, the role
     * is set to ORG_HEAD and the account is locked pending admin approval.
     */
    private AuthCredential buildCredential(RegisterRequest req) {
//        UUID userId = userServiceClient.registerUser(new CreateUserRequest(req.name(), req.email(), req.OrganizationName()));
        // TODO return this only made for test
        UUID userId = UUID.randomUUID();
        AuthCredential credential = AuthCredential.builder()
            .userId(userId)
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
}
