package org.ticketsouq.apigateway.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.ticketsouq.apigateway.model.RefreshToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres")
        .withDatabaseName("testdb")
        .withUsername("testuser")
        .withPassword("testpass");
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void shouldDeleteRevokedOrExpiredTokens() {
        UUID userId = UUID.randomUUID();

        RefreshToken activeToken = refreshTokenRepository.save(RefreshToken.builder()
            .userId(userId)
            .sessionId(UUID.randomUUID())
            .revoked(false)
            .expiryDate(Instant.now().plusSeconds(3600))
            .build());

        RefreshToken revokedToken = refreshTokenRepository.save(RefreshToken.builder()
            .userId(userId)
            .sessionId(UUID.randomUUID())
            .revoked(true)
            .expiryDate(Instant.now().plusSeconds(3600))
            .build());

        RefreshToken expiredToken = refreshTokenRepository.save(RefreshToken.builder()
            .userId(UUID.randomUUID())
            .sessionId(UUID.randomUUID())
            .revoked(false)
            .expiryDate(Instant.now().minusSeconds(3600))
            .build());

        refreshTokenRepository.deleteByRevokedTrueOrExpiryDateBefore(Instant.now());

        assertThat(refreshTokenRepository.findById(revokedToken.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(expiredToken.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(activeToken.getId())).isPresent();
    }
}
