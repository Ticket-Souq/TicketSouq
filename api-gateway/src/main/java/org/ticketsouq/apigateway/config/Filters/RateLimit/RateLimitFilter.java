package org.ticketsouq.apigateway.config.Filters.RateLimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.ticketsouq.sharedmodule.GeneralExceptions.ErrorResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final RateLimitProperties rateLimitProperties;

    // Matches request URIs against configured patterns like /api/v1/auth/**
    private final PathMatcher pathMatcher = new AntPathMatcher();

    // Per-IP token buckets, evicted after 2min of inactivity, max 100k entries
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(100_000)
            .build();

    // Grab the real client IP from proxy headers, fall back to direct remote addr
    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri;
        }
        return req.getRemoteAddr();
    }

    // Get or create a token bucket for this key (tokens refill in full every period, no bursting)
    private Bucket bucket(String key) {
        return buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rateLimitProperties.getCapacity())
                        .refillIntervally(rateLimitProperties.getRefill(), rateLimitProperties.getRefillPeriod())
                        .build())
                .build());
    }

    // Tell the client how many requests they have left and when the bucket refills
    private void writeRateLimitHeaders(HttpServletResponse res, Bucket bucket) {
        long available = bucket.getAvailableTokens();
        res.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitProperties.getCapacity()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, available - 1)));
        res.setHeader("X-RateLimit-Reset", String.valueOf(
                Instant.now().plus(rateLimitProperties.getRefillPeriod()).getEpochSecond()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // Only rate-limit paths matching the configured patterns
        String path = req.getRequestURI();
        boolean matches = rateLimitProperties.getPaths().stream()
                .anyMatch(p -> pathMatcher.match(p, path));

        if (matches) {
            String ip = resolveClientIp(req);
            Bucket bucket = bucket(ip);

            // If no tokens left, reject with 429 + rate-limit headers
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for IP: {}", ip);
                writeRateLimitHeaders(res, bucket);
                ErrorResponse body = ErrorResponse.of(429, "Too Many Requests", "Rate limit exceeded. Retry after " + rateLimitProperties.getRefillPeriod().toSeconds() + " seconds");
                res.setStatus(429);
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.getWriter().write(objectMapper.writeValueAsString(body));
                return;
            }

            // Request allowed — tell them their remaining budget
            writeRateLimitHeaders(res, bucket);
        }
        chain.doFilter(req, res);
    }

}
