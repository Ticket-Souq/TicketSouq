package org.ticketsouq.apigateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.ticketsouq.apigateway.client.UserServiceClient;
import org.ticketsouq.apigateway.dto.*;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.ticketsouq.apigateway.model.RefreshToken;
import org.ticketsouq.apigateway.model.Role;
import org.ticketsouq.apigateway.repository.AuthCredentialRepository;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateAccountRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GeneratedAccount;
import org.ticketsouq.sharedmodule.ApiGateway.event.AccountsGeneratedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.EmailVerificationEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordResetEvent;
import org.ticketsouq.sharedmodule.ApiGateway.exception.EmailAlreadyExistsException;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_HEAD_ID = UUID.randomUUID();
    @Mock private AuthTokenService authTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserServiceClient userServiceClient;
    @Mock private AuthCredentialRepository credentialRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    private AuthService authService;
    private AuthCredential customerCredential;
    private AuthCredential verifiedCredential;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authTokenService, passwordEncoder, userServiceClient,
            credentialRepository, eventPublisher, transactionManager);

        customerCredential = AuthCredential.builder()
            .userId(USER_ID)
            .email("test@test.com")
            .passwordHash("encodedPass")
            .role(Role.CUSTOMER)
            .isActive(true)
            .isVerified(true)
            .locked(false)
            .failedAttempts(0)
            .build();

        verifiedCredential = AuthCredential.builder()
            .userId(UUID.randomUUID())
            .email("verified@test.com")
            .passwordHash("encodedPass")
            .role(Role.CUSTOMER)
            .isActive(true)
            .isVerified(true)
            .locked(false)
            .failedAttempts(3)
            .build();
    }

    @Test
    void register_shouldRegisterCustomerSuccessfully() {
        RegisterRequest req = new RegisterRequest("new@test.com", "New User", "password123", null);
        when(credentialRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedNewPass");

        when(credentialRepository.save(any())).thenAnswer(invocation -> {
            AuthCredential c = invocation.getArgument(0);
            c.setUserId(UUID.randomUUID());
            return c;
        });

        authService.register(req);

        verify(credentialRepository).save(argThat(c ->
            c.getRole() == Role.CUSTOMER && c.getIsActive() && !c.getIsVerified() && !c.getLocked()));

        verify(userServiceClient).registerUser(any(CreateUserRequest.class));
        verify(eventPublisher).publishEvent(any(EmailVerificationEvent.class));
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void register_shouldRegisterOrgHeadSuccessfully() {
        RegisterRequest req = new RegisterRequest("org@test.com", "Org Head", "password123", "MyOrg");
        when(credentialRepository.existsByEmail("org@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedOrgPass");
        when(credentialRepository.save(any())).thenAnswer(invocation -> {
            AuthCredential c = invocation.getArgument(0);
            c.setUserId(UUID.randomUUID());
            return c;
        });

        authService.register(req);

        verify(credentialRepository).save(argThat(c ->
            c.getRole() == Role.ORG_HEAD && c.getLocked() && c.getLockedUntil() != null));
    }

    @Test
    void register_shouldThrowWhenEmailExists() {
        RegisterRequest req = new RegisterRequest("existing@test.com", "User", "password123", null);
        when(credentialRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req)).isInstanceOf(EmailAlreadyExistsException.class);
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void login_shouldSucceed() {
        LoginRequest req = new LoginRequest("test@test.com", "password");

        when(credentialRepository.findByEmail("test@test.com"))
            .thenReturn(Optional.of(customerCredential));

        when(passwordEncoder.matches("password", "encodedPass"))
            .thenReturn(true);

        when(authTokenService.createNewRefreshToken(USER_ID))
            .thenReturn(RefreshToken.builder().sessionId(UUID.randomUUID()).build());

        when(authTokenService.generateAccessToken(any(), any(), any(), any()))
            .thenReturn("access-token");

        when(authTokenService.generateRefreshToken(any(), any()))
            .thenReturn("refresh-token");

        AuthResponse response = authService.login(req);

        assertThat(response.access()).isEqualTo("access-token");
        assertThat(response.refresh()).isEqualTo("refresh-token");
    }

    @Test
    void login_shouldResetFailedAttemptsOnSuccessful() {
        LoginRequest req = new LoginRequest("verified@test.com", "password");

        when(credentialRepository.findByEmail("verified@test.com"))
            .thenReturn(Optional.of(verifiedCredential));

        when(passwordEncoder.matches("password", "encodedPass"))
            .thenReturn(true);

        when(authTokenService.createNewRefreshToken(any()))
            .thenReturn(RefreshToken.builder().sessionId(UUID.randomUUID()).build());

        when(authTokenService.generateAccessToken(any(), any(), any(), any()))
            .thenReturn("access");

        when(authTokenService.generateRefreshToken(any(), any()))
            .thenReturn("refresh");

        authService.login(req);

        assertThat(verifiedCredential.getFailedAttempts()).isZero();
        assertThat(verifiedCredential.getLocked()).isFalse();
    }

    @Test
    void login_shouldThrowOnBadPassword() {
        LoginRequest req = new LoginRequest("test@test.com", "wrong");
        when(credentialRepository.findByEmail("test@test.com")).thenReturn(Optional.of(customerCredential));
        when(passwordEncoder.matches("wrong", "encodedPass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Bad credentials");
    }

    @Test
    void login_shouldLockAccountAfter5FailedAttempts() {
        AuthCredential cred = AuthCredential.builder()
            .userId(UUID.randomUUID())
            .email("fail@test.com")
            .passwordHash("encoded")
            .role(Role.CUSTOMER)
            .isActive(true)
            .isVerified(true)
            .locked(false)
            .failedAttempts(4)
            .build();

        LoginRequest req = new LoginRequest("fail@test.com", "wrong");
        when(credentialRepository.findByEmail("fail@test.com")).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req)).isInstanceOf(BusinessException.class);
        assertThat(cred.getLocked()).isTrue();
        assertThat(cred.getLockedUntil()).isNotNull();
    }

    @Test
    void login_shouldThrowWhenAccountNotFound() {
        LoginRequest req = new LoginRequest("nonexistent@test.com", "password");
        when(credentialRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Account not found");
    }

    @Test
    void login_shouldThrowWhenAccountNotVerified() {
        AuthCredential unverified = AuthCredential.builder().userId(UUID.randomUUID()).email("u@t.com")
            .passwordHash("hash").role(Role.CUSTOMER).isActive(true).isVerified(false).locked(false).build();
        LoginRequest req = new LoginRequest("u@t.com", "password");
        when(credentialRepository.findByEmail("u@t.com")).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Account is not verified");
    }

    @Test
    void login_shouldThrowWhenAccountInactive() {
        AuthCredential inactive = AuthCredential.builder().userId(UUID.randomUUID()).email("i@t.com")
            .passwordHash("hash").role(Role.CUSTOMER).isActive(false).isVerified(true).locked(false).build();
        LoginRequest req = new LoginRequest("i@t.com", "password");
        when(credentialRepository.findByEmail("i@t.com")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Account is not active");
    }

    @Test
    void login_shouldThrowWhenLockedDueToFailedAttempts() {
        AuthCredential locked = AuthCredential.builder().userId(UUID.randomUUID()).email("l@t.com")
            .passwordHash("hash").role(Role.CUSTOMER).isActive(true).isVerified(true).locked(true)
            .lockedUntil(Instant.now().plus(1, ChronoUnit.HOURS)).failedAttempts(5).build();
        LoginRequest req = new LoginRequest("l@t.com", "password");
        when(credentialRepository.findByEmail("l@t.com")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class).hasMessageContaining("multiple failed login attempt");
    }

    @Test
    void login_shouldThrowWhenOrgHeadLockedPendingApproval() {
        AuthCredential orgHead = AuthCredential.builder().userId(ORG_HEAD_ID).email("org@t.com")
            .passwordHash("hash").role(Role.ORG_HEAD).isActive(true).isVerified(true).locked(true)
            .lockedUntil(Instant.parse("9999-12-31T23:59:59Z")).failedAttempts(0).build();
        LoginRequest req = new LoginRequest("org@t.com", "password");
        when(credentialRepository.findByEmail("org@t.com")).thenReturn(Optional.of(orgHead));

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Admin Approval");
    }

    @Test
    void login_shouldThrowWhenBelongsToBannedOrg() {
        AuthCredential banned = AuthCredential.builder().userId(UUID.randomUUID()).email("b@t.com")
            .passwordHash("hash").role(Role.ORG_Agent).isActive(true).isVerified(true).locked(false).build();
        LoginRequest req = new LoginRequest("b@t.com", "password");
        when(credentialRepository.findByEmail("b@t.com")).thenReturn(Optional.of(banned));
        lenient().when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(userServiceClient.isBelongToBannedOrg(banned.getUserId())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class).hasMessageContaining("banned organization");
    }

    @Test
    void refresh_shouldSucceed() {
        RefreshToken newToken = RefreshToken.builder().userId(USER_ID).sessionId(UUID.randomUUID()).build();
        when(authTokenService.refresh("old-refresh-token")).thenReturn(newToken);
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(customerCredential));
        when(authTokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("new-access");
        when(authTokenService.generateRefreshToken(any(), any())).thenReturn("new-refresh");

        AuthResponse response = authService.refresh("old-refresh-token");

        assertThat(response.access()).isEqualTo("new-access");
        assertThat(response.refresh()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_shouldThrowWhenUserNotFound() {
        RefreshToken newToken = RefreshToken.builder().userId(UUID.randomUUID()).sessionId(UUID.randomUUID()).build();
        when(authTokenService.refresh("token")).thenReturn(newToken);
        when(credentialRepository.findByUserId(newToken.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Account not found");
    }

    @Test
    void triggerVerificationEmail_shouldSend() {
        AuthCredential unverified = AuthCredential.builder().userId(UUID.randomUUID()).email("u@t.com")
            .passwordHash("hash").role(Role.CUSTOMER).isActive(true).isVerified(false).locked(false).build();
        when(credentialRepository.findByEmail("u@t.com")).thenReturn(Optional.of(unverified));
        when(authTokenService.generateEmailVerificationToken(unverified.getUserId())).thenReturn("email-token");

        authService.triggerVerificationEmail("u@t.com");

        verify(eventPublisher).publishEvent(any(EmailVerificationEvent.class));
    }

    @Test
    void triggerVerificationEmail_shouldSkipIfAlreadyVerified() {
        when(credentialRepository.findByEmail("test@test.com")).thenReturn(Optional.of(customerCredential));

        authService.triggerVerificationEmail("test@test.com");

        verify(eventPublisher, never()).publishEvent(any(EmailVerificationEvent.class));
    }

    @Test
    void verifyEmail_shouldVerify() {
        UUID userId = USER_ID;
        when(authTokenService.validateEmailToken("verify-token")).thenReturn(userId);
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(customerCredential));

        authService.verifyEmail("verify-token");

        assertThat(customerCredential.getIsVerified()).isTrue();
        verify(credentialRepository).save(customerCredential);
    }

    @Test
    void triggerPasswordReset_shouldSend() {
        when(credentialRepository.findByEmail("test@test.com")).thenReturn(Optional.of(customerCredential));
        when(authTokenService.generatePasswordResetToken(USER_ID)).thenReturn("reset-token");

        authService.triggerPasswordReset("test@test.com");

        verify(eventPublisher).publishEvent(any(PasswordResetEvent.class));
    }

    @Test
    void resetPassword_shouldReset() {
        UUID userId = USER_ID;
        when(authTokenService.validatePasswordResetToken("reset-token")).thenReturn(userId);
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(customerCredential));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newEncoded");

        authService.resetPassword(new ResetPasswordRequest("reset-token", "newPassword123"));

        assertThat(customerCredential.getPasswordHash()).isEqualTo("newEncoded");
        verify(authTokenService).invalidateAllSession(userId);
    }

    @Test
    void resetPassword_shouldThrowOnInvalidToken() {
        when(authTokenService.validatePasswordResetToken("invalid"))
            .thenThrow(new BusinessException("Invalid", HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("invalid", "newPwd")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void resetPassword_shouldWrapRuntimeException() {
        when(authTokenService.validatePasswordResetToken("bad"))
            .thenThrow(new RuntimeException("parse error"));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("bad", "newPwd")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Invalid or expired reset token");
    }

    @Test
    void changePassword_shouldChange() {
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(customerCredential));
        when(passwordEncoder.matches("currentPass", "encodedPass")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("newEncoded");

        authService.changePassword(USER_ID, new ChangePasswordRequest("currentPass", "newPass123"));

        assertThat(customerCredential.getPasswordHash()).isEqualTo("newEncoded");
        verify(authTokenService).invalidateAllSession(USER_ID);
    }

    @Test
    void changePassword_shouldThrowWhenCurrentPasswordIncorrect() {
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(customerCredential));
        when(passwordEncoder.matches("wrong", "encodedPass")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(USER_ID, new ChangePasswordRequest("wrong", "newPass123")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Current password is incorrect");
    }

    @Test
    void deactivateAccount_shouldDeactivate() {
        when(credentialRepository.findByUserId(USER_ID)).thenReturn(Optional.of(customerCredential));

        authService.deactivateAccount(USER_ID);

        assertThat(customerCredential.getIsActive()).isFalse();
        verify(authTokenService).invalidateAllSession(USER_ID);
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void unlockOrg_shouldUnlock() {
        AuthCredential orgHead = AuthCredential.builder().userId(ORG_HEAD_ID).email("org@t.com")
            .passwordHash("hash").role(Role.ORG_HEAD).isActive(true).isVerified(true).locked(true)
            .lockedUntil(Instant.parse("9999-12-31T23:59:59Z")).build();
        when(credentialRepository.findByUserId(ORG_HEAD_ID)).thenReturn(Optional.of(orgHead));

        authService.unlockOrg(ORG_HEAD_ID);

        assertThat(orgHead.getLocked()).isFalse();
        assertThat(orgHead.getLockedUntil()).isNull();
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void generateAccounts_shouldGenerate() {
        GenerateAccountRequest req = new GenerateAccountRequest(2, 3);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-gen-pass");
        when(credentialRepository.save(any())).thenAnswer(invocation -> {
            AuthCredential c = invocation.getArgument(0);
            if (c.getUserId() == null) c.setUserId(UUID.randomUUID());
            return c;
        });

        List<GeneratedAccount> accounts = authService.generateAccountsForOrg(ORG_HEAD_ID, req);

        assertThat(accounts).hasSize(5);
        assertThat(accounts.stream().filter(a -> "ORG_Agent".equals(a.Role())).count()).isEqualTo(2);
        assertThat(accounts.stream().filter(a -> "ORG_Consumer".equals(a.Role())).count()).isEqualTo(3);
        verify(userServiceClient).generateMembers(any(GenerateMembersRequest.class));
        verify(eventPublisher).publishEvent(any(AccountsGeneratedEvent.class));
        verify(credentialRepository, times(5)).save(any());
    }

    @Test
    void generateAccounts_shouldReturnEmptyWhenZeroCounts() {
        GenerateAccountRequest req = new GenerateAccountRequest(0, 0);

        List<GeneratedAccount> accounts = authService.generateAccountsForOrg(ORG_HEAD_ID, req);

        assertThat(accounts).isEmpty();
        verify(userServiceClient, never()).generateMembers(any());
    }
}
