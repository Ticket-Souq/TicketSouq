package org.ticketsouq.apigateway.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.ticketsouq.apigateway.model.Role;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
class AuthCredentialRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres")
        .withDatabaseName("testdb")
        .withUsername("testuser")
        .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private AuthCredentialRepository authCredentialRepository;


    @Test
    void shouldUnlockExpiredAccounts() {
        AuthCredential locked = authCredentialRepository.save(AuthCredential.builder()
                .email("locked@test.com")
                .passwordHash("hash")
                .role(Role.ORG_HEAD)
                .isActive(true)
                .isVerified(true)
                .locked(true)
                .lockedUntil(Instant.now().minusSeconds(60))
                .build());

        authCredentialRepository.unlockExpiredAccounts(Instant.now());

        AuthCredential refreshed = authCredentialRepository.findById(locked.getUserId()).get();
        assertThat(refreshed.getLocked()).isFalse();
        assertThat(refreshed.getLockedUntil()).isNull();
    }

    @Test
    void shouldNotUnlockNonExpiredAccounts() {
        AuthCredential stillLocked = authCredentialRepository.save(AuthCredential.builder()
                .email("still-locked@test.com")
                .passwordHash("hash")
                .role(Role.ORG_HEAD)
                .isActive(true)
                .isVerified(true)
                .locked(true)
                .lockedUntil(Instant.now().plusSeconds(3600))
                .build());

        authCredentialRepository.unlockExpiredAccounts(Instant.now());

        AuthCredential refreshed = authCredentialRepository.findById(stillLocked.getUserId()).get();
        assertThat(refreshed.getLocked()).isTrue();
        assertThat(refreshed.getLockedUntil()).isNotNull();
    }
}
