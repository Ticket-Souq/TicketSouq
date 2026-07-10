package org.ticketsouq.apigateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.apigateway.model.RefreshToken;
import org.ticketsouq.apigateway.model.TokenType;
import org.ticketsouq.apigateway.repository.AccessTokenRepository;
import org.ticketsouq.apigateway.repository.RefreshTokenRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRepository accessTokenRepository;

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.access-token-expiry-ms}")
    private long accessExpiry;
    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshExpiry;
    @Value("${jwt.email-token-expiry-ms}")
    private long emailExpiry;
    @Value("${jwt.password-reset-expiry-ms}")
    private long passwordResetExpiry;
    @Value("${jwt.issuer}")
    private String issuer;

    private SecretKey secretKey;

    // ── Initialization ────────────────────────────────────────────────────────

    /*
     * Initializes the HMAC signing key from the configured secret.
     * 1. Converts the secret string to bytes (UTF-8)
     * 2. Validates the key is at least 32 bytes for HMAC-SHA256
     * 3. Creates a reusable SecretKey instance
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes (256 bits) for HMAC-SHA256");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Access token ──────────────────────────────────────────────────────────

    /*
     * Generates an access token, stores its JTI in Redis, and registers it under
     * the user's sessions set so it can be bulk-revoked later.
     */
    public String generateAccessToken(UUID userId, String email, List<String> roles, UUID sessionId) {
        String jti = UUID.randomUUID().toString();

        List<String> rawRoles = roles.stream()
            .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
            .collect(Collectors.toList());

        String token = buildAccessToken(userId.toString(), jti, email, rawRoles, sessionId);
        accessTokenRepository.insertToRedis(userId.toString(), jti, Duration.ofMillis(accessExpiry));
        return token;
    }

    /*
     * Builds the raw JWT for an access token with all required claims.
     * 1. Sets issuer, subject (userId), JTI, email, roles, type=ACCESS, session ID
     * 2. Signs with the HMAC secret key
     */
    private String buildAccessToken(String userId, String jti, String email, List<String> roles, UUID sessionId) {
        return Jwts.builder()
            .issuer(issuer)
            .subject(userId)
            .id(jti)
            .claim("email", email)
            .claim("roles", roles)
            .claim("type", TokenType.ACCESS.name())
            .claim("sid", sessionId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessExpiry))
            .signWith(secretKey)
            .compact();
    }

    /*
     * Verifies the access token is valid:
     * 1. Parses and verifies the JWT signature
     * 2. Checks the token type is ACCESS
     * 3. Checks the JTI still exists in Redis (not expired or revoked)
     */
    public boolean isAccessTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            if (!TokenType.ACCESS.name().equals(claims.get("type"))) return false;
            return accessTokenRepository.existsInRedis(claims.getId());
        } catch (Exception e) {
            return false;
        }
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    /*
     * Creates a new refresh token row (DB entity that tracks a user's session).
     */
    public RefreshToken createNewRefreshToken(UUID userId) {
        RefreshToken session = RefreshToken.builder()
            .sessionId(UUID.randomUUID())
            .userId(userId)
            .revoked(false)
            .expiryDate(Instant.now().plusMillis(refreshExpiry))
            .build();
        return refreshTokenRepository.save(session);
    }

    /*
     * Builds the JWT string that encodes the refresh token's session ID.
     */
    public String generateRefreshToken(UUID userId, UUID sessionId) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("sid", sessionId.toString())
            .claim("type", TokenType.REFRESH.name())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshExpiry))
            .signWith(secretKey)
            .compact();
    }

    /*
     * Rotates a refresh token (token theft detection):
     * 1. Parses the JWT and extracts the session ID
     * 2. Looks up the DB row for that session (PESSIMISTIC_WRITE lock)
     * 3. If already revoked → token reuse detected → nuke ALL sessions for that user
     * 4. If expired → reject
     * 5. Marks old row as revoked, creates a new row, returns it
     */
    @Transactional
    public RefreshToken refresh(String refreshToken) {
        Claims claims = parseToken(refreshToken);
        assertTokenType(claims, TokenType.REFRESH);

        UUID oldSessionId = UUID.fromString(claims.get("sid", String.class));
        RefreshToken oldSession = refreshTokenRepository.findRefreshTokenBySessionId(oldSessionId)
            .orElseThrow(() -> new BusinessException("Invalid refresh token, please log in again", HttpStatus.UNAUTHORIZED));

        if (oldSession.isRevoked()) {
            UUID userId = oldSession.getUserId();
            refreshTokenRepository.deleteByUserId(userId);
            accessTokenRepository.removeAllActiveSessionsFromRedis(userId);
            throw new BusinessException("Refresh token reuse detected — all sessions invalidated", HttpStatus.UNAUTHORIZED);
        }

        if (oldSession.getExpiryDate().isBefore(Instant.now())) {
            throw new BusinessException("Refresh token expired, please log in again", HttpStatus.UNAUTHORIZED);
        }

        oldSession.setRevoked(true);

        RefreshToken newSession = RefreshToken.builder()
            .sessionId(UUID.randomUUID())
            .userId(oldSession.getUserId())
            .revoked(false)
            .expiryDate(Instant.now().plusMillis(refreshExpiry))
            .build();

        return refreshTokenRepository.save(newSession);
    }

    // ── Session management ────────────────────────────────────────────────────

    /*
     * Invalidates a single session:
     * 1. Parses the access token and validates it's type=ACCESS
     * 2. Deletes the refresh token row for that session
     * 3. Removes the JTI from Redis
     */
    @Transactional
    public void invalidateSession(String accessToken) {
        Claims claims = parseToken(accessToken);
        assertTokenType(claims, TokenType.ACCESS);

        UUID sessionId = UUID.fromString(claims.get("sid", String.class));
        refreshTokenRepository.deleteBySessionId(sessionId);
        accessTokenRepository.removeSessionFromRedis(claims);
    }

    /*
     * Invalidates ALL sessions for a user:
     * 1. Deletes all refresh token rows for that userId
     * 2. Removes all JTI entries from Redis for that userId
     */
    @Transactional
    public void invalidateAllSession(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
        accessTokenRepository.removeAllActiveSessionsFromRedis(userId);
    }

    // ── Email verification token ──────────────────────────────────────────────

    /*
     * Generates a short-lived JWT for email verification.
     */
    public String generateEmailVerificationToken(UUID userId) {
        return buildShortLivedToken(userId.toString(), TokenType.EMAIL_VERIFICATION, emailExpiry);
    }

    /*
     * Validates an email verification token:
     * 1. Parses and verifies the JWT signature
     * 2. Checks the token type matches
     * 3. Checks expiration
     */
    public UUID validateEmailToken(String token) {
        Claims claims = parseAndValidate(token, TokenType.EMAIL_VERIFICATION);
        return UUID.fromString(claims.getSubject());
    }

    // ── Password reset token ──────────────────────────────────────────────────

    /*
     * Generates a short-lived JWT for password reset.
     */
    public String generatePasswordResetToken(UUID userId) {
        return buildShortLivedToken(userId.toString(), TokenType.PASSWORD_RESET, passwordResetExpiry);
    }

    /*
     * Validates a password reset token:
     * 1. Parses and verifies the JWT signature
     * 2. Checks the token type matches
     * 3. Checks expiration
     */
    public UUID validatePasswordResetToken(String token) {
        Claims claims = parseAndValidate(token, TokenType.PASSWORD_RESET);
        return UUID.fromString(claims.getSubject());
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /*
     * Parses a JWT and verifies its signature.
     * Does NOT check expiration — callers are responsible for that.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /*
     * Asserts the token's "type" claim matches the expected value.
     * 1. Reads the "type" claim from the parsed claims
     * 2. Throws if it doesn't match the expected TokenType
     */
    private void assertTokenType(Claims claims, TokenType expected) {
        if (!expected.name().equals(claims.get("type")))
            throw new BusinessException("Invalid token type", HttpStatus.UNAUTHORIZED);
    }

    /*
     * Parses, type-checks, and expiration-checks a short-lived token.
     * 1. Parses the JWT and verifies the signature
     * 2. Validates the token type matches the expected type
     * 3. Rejects if the token has expired
     */
    private Claims parseAndValidate(String token, TokenType expected) {
        try {
            Claims claims = parseToken(token);
            assertTokenType(claims, expected);
            if (claims.getExpiration().before(new Date()))
                throw new BusinessException("Invalid or expired " + expected.name().toLowerCase().replace('_', ' ') + " token", HttpStatus.BAD_REQUEST);
            return claims;
        } catch (ExpiredJwtException e) {
            throw new BusinessException("Invalid or expired " + expected.name().toLowerCase().replace('_', ' ') + " token", HttpStatus.BAD_REQUEST);
        }
    }

    /*
     * Builds a short-lived JWT (email verification or password reset).
     * 1. Sets subject, type claim, issued-at, and expiration
     * 2. Signs with the HMAC secret key
     */
    private String buildShortLivedToken(String subject, TokenType type, long expiryMs) {
        return Jwts.builder()
            .subject(subject)
            .claim("type", type.name())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiryMs))
            .signWith(secretKey)
            .compact();
    }
}
