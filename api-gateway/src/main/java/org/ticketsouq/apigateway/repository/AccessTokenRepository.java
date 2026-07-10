package org.ticketsouq.apigateway.repository;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AccessTokenRepository {

    // auth:jti:<jti>      → userId  (TTL = accessExpiry)
    private static final String JTI_KEY_PREFIX  = "auth:jti:";
    // auth:user:<userId>  → Set of active JTIs
    private static final String USER_SET_PREFIX = "auth:user:";

    private final StringRedisTemplate redis;

    private static final RedisScript<Long> INSERT_TO_REDIS = RedisScript.of("""
                redis.call('SETEX', KEYS[1], ARGV[1], ARGV[2])
                redis.call('SADD', KEYS[2], ARGV[3])
                local current = redis.call('TTL', KEYS[2])
                local newTtl  = tonumber(ARGV[1])
                if current == -1 or current < newTtl then
                    redis.call('EXPIRE', KEYS[2], newTtl)
                end
                return 1
                """, Long.class);

    private static final RedisScript<Long> REMOVE_DEAD_SESSIONS = RedisScript.of("""
        local set_key = KEYS[1]
        local members = redis.call('SMEMBERS', set_key)

        if #members == 0 then
            return 0
        end

        local dead = {}

        for _, jti in ipairs(members) do
            local jti_key = KEYS[2] .. jti
            local val = redis.call('GET', jti_key)
            if val == false then
                table.insert(dead, jti)
            end
        end

        if #dead > 0 then
            redis.call('SREM', set_key, unpack(dead))
        end

        return #dead
        """, Long.class);

    private static final RedisScript<Long> REVOKE_SESSION = RedisScript.of("""
        redis.call('DEL',  KEYS[1])
        redis.call('SREM', KEYS[2], ARGV[1])
        return 1
        """, Long.class);

    private static final RedisScript<Long> REVOKE_ALL_SESSION = RedisScript.of("""
                local members = redis.call('SMEMBERS', KEYS[1])
                local prefix  = ARGV[1]
                for _, jti in ipairs(members) do
                    redis.call('DEL', prefix .. jti)
                end
                redis.call('DEL', KEYS[1])
                return #members
                """, Long.class);


    // ── Write ────────────────────────────────────────────────────────────────

    public void insertToRedis(String userId, String jti, Duration ttl) {
        redis.execute(INSERT_TO_REDIS, List.of(jtiKey(jti), userSetKey(userId)), String.valueOf(ttl.getSeconds()), userId, jti);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    public boolean existsInRedis(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(jtiKey(jti)));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void removeDeadSessions(UUID userId) {
        Long removed = redis.execute(REMOVE_DEAD_SESSIONS, List.of(userSetKey(String.valueOf(userId)), JTI_KEY_PREFIX));
        log.debug("Removed {} dead session(s) for userId={}", removed, userId);
    }

    public void removeSessionFromRedis(Claims claims) {
        String jti    = claims.getId();
        String userId = claims.getSubject();
        redis.execute(REVOKE_SESSION, List.of(jtiKey(jti), userSetKey(userId)), jti);
    }

    public void removeAllActiveSessionsFromRedis(UUID userId) {
        Long count = redis.execute(REVOKE_ALL_SESSION, List.of(userSetKey(userId.toString())), JTI_KEY_PREFIX);
        log.debug("Invalidated {} access token(s) for userId={}", count, userId);
    }

    // ── Key helpers ───────────────────────────────────────────────────────────

    private String jtiKey(String jti)      { return JTI_KEY_PREFIX  + jti; }
    private String userSetKey(String userId) { return USER_SET_PREFIX + userId; }
}
