package org.ticketsouq.apigateway.config.Filters.RateLimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private FilterChain filterChain;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RateLimitProperties properties = new RateLimitProperties();
        properties.setPaths(List.of("/api/v1/auth/**"));
        properties.setCapacity(5);
        properties.setRefill(5);
        properties.setRefillPeriod(Duration.ofMinutes(1));
        properties.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost:3000"));
        filter = new RateLimitFilter(objectMapper, properties);
    }

    @AfterEach
    void tearDown() {
        filter = null;
    }

    @Nested
    class MatchingPaths {
        // Verify that a request within the token bucket limit is allowed (not 429)
        @Test
        void shouldAllowRequestUnderLimit() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        // Verify that requests exceeding the 5-token bucket are rejected with 429
        @Test
        void shouldRateLimitWhenExceeded() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest();
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.setRequestURI("/api/v1/auth/login");
            req.setRemoteAddr("10.0.0.1");

            for (int i = 0; i < 6; i++) {
                filter.doFilterInternal(req, resp, filterChain);
            }

            assertThat(resp.getStatus()).isEqualTo(429);
            assertThat(resp.getContentType()).isEqualTo("application/json");
            assertThat(resp.getContentAsString()).contains("Too Many Requests");
        }

        // Verify that allowed requests include X-RateLimit headers (limit, remaining, reset)
        @Test
        void shouldIncludeRateLimitHeadersWhenMatched() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isNotNull();
            assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
        }
    }

    @Nested
    class FrontendBypass {

        // Verify that a request with Origin matching allowed-origins skips rate limiting
        @Test
        void shouldBypassRateLimitWhenOriginMatchesAllowedOrigin() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("Origin", "http://localhost:4200");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        // Verify that a request with Referer matching an allowed origin also bypasses rate limiting
        @Test
        void shouldBypassRateLimitWhenRefererMatchesAllowedOrigin() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("Referer", "http://localhost:3000/login");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
        }

        // Verify that a non-matching Origin header does not bypass rate limiting
        @Test
        void shouldNotBypassWhenOriginDoesNotMatch() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("Origin", "http://NotMyWebsite.com");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        }

        // Verify that requests without Origin or Referer headers are rate-limited normally
        @Test
        void shouldNotBypassWhenNoOriginOrRefererPresent() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        }

        // Verify that frontend requests do not consume tokens from the IP bucket (even when IP is exhausted)
        @Test
        void shouldLetFrontendRequestConsumeNoTokens() throws Exception {

            MockHttpServletRequest req = new MockHttpServletRequest();
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.setRequestURI("/api/v1/auth/login");
            req.setRemoteAddr("10.0.0.99");

            for (int i = 0; i < 6; i++) {
                filter.doFilterInternal(req, resp, filterChain);
            }
            assertThat(resp.getStatus()).isEqualTo(429);


            MockHttpServletRequest frontendReq = new MockHttpServletRequest();
            MockHttpServletResponse frontendResp = new MockHttpServletResponse();
            frontendReq.setRequestURI("/api/v1/auth/login");
            frontendReq.setRemoteAddr("10.0.0.99");
            frontendReq.addHeader("Origin", "http://localhost:4200");
            filter.doFilterInternal(frontendReq, frontendResp, filterChain);

            assertThat(frontendResp.getStatus()).isNotEqualTo(429);
            assertThat(frontendResp.getHeader("X-RateLimit-Limit")).isNull();
        }
    }

    @Nested
    class ClientIpResolution {

        // Verify that X-Forwarded-For header is used as the client IP for rate limiting
        @Test
        void shouldUseXForwardedForHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
            request.setRemoteAddr("192.168.1.1");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        }

        // Verify that X-Real-IP header is used as fallback when X-Forwarded-For is absent
        @Test
        void shouldUseXRealIPHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.addHeader("X-Real-IP", "10.0.0.5");
            request.setRemoteAddr("192.168.1.1");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        }

        // Verify that request.getRemoteAddr() is used when no proxy headers are present
        @Test
        void shouldFallbackToRemoteAddr() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.99");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        }

        // Verify that a blank X-Forwarded-For header is ignored and falls back to RemoteAddr
        @Test
        void shouldHandleBlankXForwardedFor() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.setRequestURI("/api/v1/auth/login");
            request.addHeader("X-Forwarded-For", " ");
            request.setRemoteAddr("10.0.0.99");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        }

        // Verify that different IPs have independent token buckets (one IP exhausting does not affect another)
        @Test
        void shouldDifferentiateByIp() throws Exception {
            String ip1 = "10.0.0.201";
            String ip2 = "10.0.0.202";

            MockHttpServletRequest req = new MockHttpServletRequest();
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.setRequestURI("/api/v1/auth/login");
            req.setRemoteAddr(ip1);

            for (int i = 0; i < 6; i++) {
                filter.doFilterInternal(req, resp, filterChain);
            }
            assertThat(resp.getStatus()).isEqualTo(429);


            MockHttpServletRequest allowedReq = new MockHttpServletRequest();
            MockHttpServletResponse allowedResp = new MockHttpServletResponse();
            allowedReq.setRequestURI("/api/v1/auth/login");
            allowedReq.setRemoteAddr(ip2);
            filter.doFilterInternal(allowedReq, allowedResp, filterChain);
            assertThat(allowedResp.getStatus()).isNotEqualTo(429);
        }
    }
}
