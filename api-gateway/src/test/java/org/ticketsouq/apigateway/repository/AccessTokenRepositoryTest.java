package org.ticketsouq.apigateway.repository;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AccessTokenRepositoryTest.TestApp.class)
@Testcontainers(disabledWithoutDocker = true)
class AccessTokenRepositoryTest {

    @Configuration
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class TestApp {
        @Bean
        AccessTokenRepository accessTokenRepository(StringRedisTemplate redis) {
            return new AccessTokenRepository(redis);
        }
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private AccessTokenRepository accessTokenRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private String userId;
    private String jti;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        userId = UUID.randomUUID().toString();
        jti = UUID.randomUUID().toString();
    }

    @Test
    void shouldInsertAndFindInRedis() {
        accessTokenRepository.insertToRedis(userId, jti, Duration.ofMinutes(5));
        assertThat(accessTokenRepository.existsInRedis(jti)).isTrue();
    }

    @Test
    void shouldNotFindNonExistentJti() {
        assertThat(accessTokenRepository.existsInRedis("nonexistent")).isFalse();
    }

    @Test
    void shouldRemoveSessionFromRedis() {
        accessTokenRepository.insertToRedis(userId, jti, Duration.ofMinutes(5));
        assertThat(accessTokenRepository.existsInRedis(jti)).isTrue();

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(jti);
        when(claims.getSubject()).thenReturn(userId);

        accessTokenRepository.removeSessionFromRedis(claims);
        assertThat(accessTokenRepository.existsInRedis(jti)).isFalse();
    }

    @Test
    void shouldRemoveAllActiveSessionsFromRedis() {
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();
        accessTokenRepository.insertToRedis(userId, jti1, Duration.ofMinutes(5));
        accessTokenRepository.insertToRedis(userId, jti2, Duration.ofMinutes(5));

        accessTokenRepository.removeAllActiveSessionsFromRedis(UUID.fromString(userId));

        assertThat(accessTokenRepository.existsInRedis(jti1)).isFalse();
        assertThat(accessTokenRepository.existsInRedis(jti2)).isFalse();
    }

    @Test
    void shouldRemoveDeadSessions() {
        accessTokenRepository.insertToRedis(userId, jti, Duration.ofMinutes(5));
        assertThat(accessTokenRepository.existsInRedis(jti)).isTrue();

        accessTokenRepository.removeDeadSessions(UUID.fromString(userId));
        assertThat(accessTokenRepository.existsInRedis(jti)).isTrue();

        redisTemplate.delete("auth:jti:" + jti);
        accessTokenRepository.removeDeadSessions(UUID.fromString(userId));
    }
}
