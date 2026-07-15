package org.ticketsouq.apigateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.ticketsouq.apigateway.model.RefreshToken;
import org.ticketsouq.apigateway.model.TokenType;
import org.ticketsouq.apigateway.repository.AccessTokenRepository;
import org.ticketsouq.apigateway.repository.RefreshTokenRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final String SECRET = "ThisIsAVeryLongSecretKeyThatMustBeAtLeast32BytesLongForHS256";
    private static final long ACCESS_EXPIRY = 300000;
    private static final long REFRESH_EXPIRY = 604800000;
    private static final long EMAIL_EXPIRY = 300000;
    private static final long PASSWORD_RESET_EXPIRY = 300000;
    private static final String ISSUER = "ticket-souq";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AccessTokenRepository accessTokenRepository;
    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        authTokenService = new AuthTokenService(refreshTokenRepository, accessTokenRepository);
        ReflectionTestUtils.setField(authTokenService, "secret", SECRET);
        ReflectionTestUtils.setField(authTokenService, "accessExpiry", ACCESS_EXPIRY);
        ReflectionTestUtils.setField(authTokenService, "refreshExpiry", REFRESH_EXPIRY);
        ReflectionTestUtils.setField(authTokenService, "emailExpiry", EMAIL_EXPIRY);
        ReflectionTestUtils.setField(authTokenService, "passwordResetExpiry", PASSWORD_RESET_EXPIRY);
        ReflectionTestUtils.setField(authTokenService, "issuer", ISSUER);
        authTokenService.init();
    }

    @Test
    void init_shouldThrowWhenSecretTooShort() {
        AuthTokenService svc = new AuthTokenService(refreshTokenRepository, accessTokenRepository);
        ReflectionTestUtils.setField(svc, "secret", "too-short");

        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32 bytes");
    }


    @Test
    void generateAccessToken_shouldGenerateAndStore() {
        List<String> roles = List.of("CUSTOMER");

        String token = authTokenService.generateAccessToken(USER_ID, "test@test.com", roles, SESSION_ID);

        assertThat(token).isNotNull();
        verify(accessTokenRepository).insertToRedis(eq(USER_ID.toString()), anyString(), any(Duration.class));
        Claims claims = parseToken(token);
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("email")).isEqualTo("test@test.com");
        assertThat(claims.get("type")).isEqualTo(TokenType.ACCESS.name());
        assertThat(claims.get("sid")).isEqualTo(SESSION_ID.toString());
    }

    @Test
    void isAccessTokenValid_shouldReturnTrueForValid() {
        String token = createTestAccessToken();
        String jti = parseToken(token).getId();
        when(accessTokenRepository.existsInRedis(jti)).thenReturn(true);

        assertThat(authTokenService.isAccessTokenValid(token)).isTrue();
    }

    @Test
    void isAccessTokenValid_shouldRejectNotInRedis() {
        String token = createTestAccessToken();
        String jti = parseToken(token).getId();
        when(accessTokenRepository.existsInRedis(jti)).thenReturn(false);

        assertThat(authTokenService.isAccessTokenValid(token)).isFalse();
    }

    @Test
    void isAccessTokenValid_shouldRejectWrongType() {
        String token = Jwts.builder()
            .subject(USER_ID.toString()).claim("type", TokenType.REFRESH.name())
            .id(UUID.randomUUID().toString())
            .issuedAt(new java.util.Date())
            .expiration(new java.util.Date(System.currentTimeMillis() + ACCESS_EXPIRY))
            .signWith(getKey()).compact();

        assertThat(authTokenService.isAccessTokenValid(token)).isFalse();
        verify(accessTokenRepository, never()).existsInRedis(anyString());
    }

    @Test
    void isAccessTokenValid_shouldReturnFalseForExpiredToken() {
        AuthTokenService svc = new AuthTokenService(refreshTokenRepository, accessTokenRepository);
        ReflectionTestUtils.setField(svc, "secret", SECRET);
        ReflectionTestUtils.setField(svc, "accessExpiry", -1000L);
        ReflectionTestUtils.setField(svc, "refreshExpiry", REFRESH_EXPIRY);
        ReflectionTestUtils.setField(svc, "emailExpiry", EMAIL_EXPIRY);
        ReflectionTestUtils.setField(svc, "passwordResetExpiry", PASSWORD_RESET_EXPIRY);
        ReflectionTestUtils.setField(svc, "issuer", ISSUER);
        svc.init();

        assertThat(svc.isAccessTokenValid(svc.generateAccessToken(USER_ID, "t@t.com", List.of("C"), SESSION_ID))).isFalse();
    }

    @Test
    void createNewRefreshToken_shouldCreate() {
        RefreshToken saved = RefreshToken.builder()
            .id(UUID.randomUUID())
            .sessionId(UUID.randomUUID())
            .userId(USER_ID)
            .revoked(false)
            .expiryDate(Instant.now().plusMillis(REFRESH_EXPIRY))
            .build();
        when(refreshTokenRepository.save(any())).thenReturn(saved);

        assertThat(authTokenService.createNewRefreshToken(USER_ID)).isSameAs(saved);
    }

    @Test
    void generateRefreshToken_shouldGenerateJwt() {
        String token = authTokenService.generateRefreshToken(USER_ID, SESSION_ID);

        Claims claims = parseToken(token);
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("sid")).isEqualTo(SESSION_ID.toString());
        assertThat(claims.get("type")).isEqualTo(TokenType.REFRESH.name());
    }

    @Test
    void refresh_shouldRotateToken() {

        UUID oldSessionId = UUID.randomUUID();

        RefreshToken oldToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .sessionId(oldSessionId)
            .userId(USER_ID)
            .revoked(false)
            .expiryDate(Instant.now().plusMillis(REFRESH_EXPIRY))
            .build();
        String oldTokenJwt = authTokenService.generateRefreshToken(USER_ID, oldSessionId);

        when(refreshTokenRepository.findRefreshTokenBySessionId(oldSessionId)).thenReturn(Optional.of(oldToken));

        RefreshToken newSaved = RefreshToken.builder()
            .id(UUID.randomUUID())
            .sessionId(UUID.randomUUID())
            .userId(USER_ID)
            .revoked(false)
            .expiryDate(Instant.now().plusMillis(REFRESH_EXPIRY))
            .build();
        when(refreshTokenRepository.save(any())).thenReturn(newSaved);

        RefreshToken result = authTokenService.refresh(oldTokenJwt);

        assertThat(result).isSameAs(newSaved);
        assertThat(oldToken.isRevoked()).isTrue();
    }

    @Test
    void refresh_shouldDetectReuse() {
        UUID oldSessionId = UUID.randomUUID();
        RefreshToken revoked = RefreshToken.builder()
            .id(UUID.randomUUID()).sessionId(oldSessionId).userId(USER_ID).revoked(true)
            .expiryDate(Instant.now().plusMillis(REFRESH_EXPIRY)).build();
        String jwt = authTokenService.generateRefreshToken(USER_ID, oldSessionId);

        when(refreshTokenRepository.findRefreshTokenBySessionId(oldSessionId)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authTokenService.refresh(jwt)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Refresh token reuse detected");
        verify(refreshTokenRepository).deleteByUserId(USER_ID);
        verify(accessTokenRepository).removeAllActiveSessionsFromRedis(USER_ID);
    }

    @Test
    void refresh_shouldRejectExpired() {
        UUID oldSessionId = UUID.randomUUID();
        RefreshToken expired = RefreshToken.builder()
            .id(UUID.randomUUID()).sessionId(oldSessionId).userId(USER_ID).revoked(false)
            .expiryDate(Instant.now().minusSeconds(1)).build();
        String jwt = authTokenService.generateRefreshToken(USER_ID, oldSessionId);

        when(refreshTokenRepository.findRefreshTokenBySessionId(oldSessionId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authTokenService.refresh(jwt)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Refresh token expired");
    }

    @Test
    void refresh_shouldRejectNonExistentSession() {
        UUID oldSessionId = UUID.randomUUID();
        String jwt = authTokenService.generateRefreshToken(USER_ID, oldSessionId);

        when(refreshTokenRepository.findRefreshTokenBySessionId(oldSessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.refresh(jwt)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refresh_shouldRejectWrongType() {
        String accessJwt = createTestAccessToken();

        assertThatThrownBy(() -> authTokenService.refresh(accessJwt)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid token type");
    }

    @Test
    void validateEmailToken_shouldValidate() {
        String token = authTokenService.generateEmailVerificationToken(USER_ID);
        assertThat(authTokenService.validateEmailToken(token)).isEqualTo(USER_ID);
    }

    @Test
    void validateEmailToken_shouldRejectExpired() {
        AuthTokenService svc = new AuthTokenService(refreshTokenRepository, accessTokenRepository);
        ReflectionTestUtils.setField(svc, "secret", SECRET);
        ReflectionTestUtils.setField(svc, "accessExpiry", ACCESS_EXPIRY);
        ReflectionTestUtils.setField(svc, "refreshExpiry", REFRESH_EXPIRY);
        ReflectionTestUtils.setField(svc, "emailExpiry", -1000L);
        ReflectionTestUtils.setField(svc, "passwordResetExpiry", PASSWORD_RESET_EXPIRY);
        ReflectionTestUtils.setField(svc, "issuer", ISSUER);
        svc.init();
        String token = svc.generateEmailVerificationToken(USER_ID);

        assertThatThrownBy(() -> svc.validateEmailToken(token)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("expired email verification token");
    }

    @Test
    void validateEmailToken_shouldRejectWrongType() {
        String token = authTokenService.generatePasswordResetToken(USER_ID);
        assertThatThrownBy(() -> authTokenService.validateEmailToken(token)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid token type");
    }

    @Test
    void validatePasswordResetToken_shouldValidate() {
        String token = authTokenService.generatePasswordResetToken(USER_ID);
        assertThat(authTokenService.validatePasswordResetToken(token)).isEqualTo(USER_ID);
    }

    @Test
    void validatePasswordResetToken_shouldRejectExpired() {
        AuthTokenService svc = new AuthTokenService(refreshTokenRepository, accessTokenRepository);
        ReflectionTestUtils.setField(svc, "secret", SECRET);
        ReflectionTestUtils.setField(svc, "accessExpiry", ACCESS_EXPIRY);
        ReflectionTestUtils.setField(svc, "refreshExpiry", REFRESH_EXPIRY);
        ReflectionTestUtils.setField(svc, "emailExpiry", EMAIL_EXPIRY);
        ReflectionTestUtils.setField(svc, "passwordResetExpiry", -1000L);
        ReflectionTestUtils.setField(svc, "issuer", ISSUER);
        svc.init();
        String token = svc.generatePasswordResetToken(USER_ID);

        assertThatThrownBy(() -> svc.validatePasswordResetToken(token)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("expired password reset token");
    }

    private String createTestAccessToken() {
        return authTokenService.generateAccessToken(USER_ID, "test@test.com", List.of("CUSTOMER"), SESSION_ID);
    }

    private Claims parseToken(String token) {
        return Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
