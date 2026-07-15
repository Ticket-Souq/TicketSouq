package org.ticketsouq.apigateway.config.Filters;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.ticketsouq.apigateway.dto.AuthResponse;
import org.ticketsouq.apigateway.service.AuthService;
import org.ticketsouq.apigateway.service.AuthTokenService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private AuthService authService;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String NEW_ACCESS = "new-access-token";
    private static final String NEW_REFRESH = "new-refresh-token";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(authTokenService, authService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    // ── Only access token (Authentication header) ─────────────────────────

    @Test
    void shouldAuthenticateWithValidAccessToken() throws Exception {
        String token = "valid-jwt";
        request.addHeader("Authentication", token);

        Claims claims = mock(Claims.class);
        when(authTokenService.isAccessTokenValid(token)).thenReturn(true);
        when(authTokenService.parseToken(token)).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("roles")).thenReturn(List.of("CUSTOMER"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-id");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipWhenNoHeaders() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authTokenService, authService);
    }

    @Test
    void shouldSkipWhenAccessTokenEmpty() throws Exception {
        request.addHeader("Authentication", "");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authTokenService, authService);
    }

    @Test
    void shouldSkipWhenAccessTokenInvalid() throws Exception {
        request.addHeader("Authentication", "invalid");
        when(authTokenService.isAccessTokenValid("invalid")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleAuthenticationExceptionGracefully() throws Exception {
        request.addHeader("Authentication", "bad");
        when(authTokenService.isAccessTokenValid("bad")).thenThrow(new RuntimeException("JWT error"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleNullRoles() throws Exception {
        request.addHeader("Authentication", "token-no-roles");

        Claims claims = mock(Claims.class);
        when(authTokenService.isAccessTokenValid("token-no-roles")).thenReturn(true);
        when(authTokenService.parseToken("token-no-roles")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("roles")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void shouldHandleRolesWithRolePrefix() throws Exception {
        request.addHeader("Authentication", "token-prefixed");

        Claims claims = mock(Claims.class);
        when(authTokenService.isAccessTokenValid("token-prefixed")).thenReturn(true);
        when(authTokenService.parseToken("token-prefixed")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("roles")).thenReturn(List.of("ROLE_ADMIN"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    // ── Refresh flow (both Authentication + refresh headers) ──────────────

    @Test
    void shouldAuthenticateWithValidAccessTokenIgnoringRefresh() throws Exception {
        request.addHeader("Authentication", "valid-access");
        request.addHeader("refresh", "any-refresh");

        Claims claims = mock(Claims.class);
        when(authTokenService.isAccessTokenValid("valid-access")).thenReturn(true);
        when(authTokenService.parseToken("valid-access")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("roles")).thenReturn(List.of("CUSTOMER"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-id");
        assertThat(response.getHeader("Authentication")).isNull();
        assertThat(response.getHeader("refresh")).isNull();
        verifyNoInteractions(authService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRefreshWhenAccessTokenInvalidAndRefreshPresent() throws Exception {
        request.addHeader("Authentication", "expired-access");
        request.addHeader("refresh", "valid-refresh");

        when(authTokenService.isAccessTokenValid("expired-access")).thenReturn(false);
        when(authService.refresh("valid-refresh")).thenReturn(new AuthResponse(NEW_ACCESS, NEW_REFRESH));

        Claims claims = mock(Claims.class);
        when(authTokenService.parseToken(NEW_ACCESS)).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("roles")).thenReturn(List.of("CUSTOMER"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-id");
        assertThat(response.getHeader("Authentication")).isEqualTo(NEW_ACCESS);
        assertThat(response.getHeader("refresh")).isEqualTo(NEW_REFRESH);
        verify(authService).refresh("valid-refresh");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldStayAnonymousWhenBothTokensInvalid() throws Exception {
        request.addHeader("Authentication", "expired-access");
        request.addHeader("refresh", "expired-refresh");

        when(authTokenService.isAccessTokenValid("expired-access")).thenReturn(false);
        when(authService.refresh("expired-refresh")).thenThrow(new RuntimeException("Refresh expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getHeader("Authentication")).isNull();
        assertThat(response.getHeader("refresh")).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRefreshWhenOnlyRefreshToken() throws Exception {
        request.addHeader("refresh", "valid-refresh");

        when(authService.refresh("valid-refresh")).thenReturn(new AuthResponse(NEW_ACCESS, NEW_REFRESH));

        Claims claims = mock(Claims.class);
        when(authTokenService.parseToken(NEW_ACCESS)).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("roles")).thenReturn(List.of("CUSTOMER"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-id");
        assertThat(response.getHeader("Authentication")).isEqualTo(NEW_ACCESS);
        assertThat(response.getHeader("refresh")).isEqualTo(NEW_REFRESH);
        verify(authService).refresh("valid-refresh");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldStayAnonymousWhenOnlyRefreshTokenFails() throws Exception {
        request.addHeader("refresh", "invalid-refresh");

        when(authService.refresh("invalid-refresh")).thenThrow(new RuntimeException("Invalid refresh"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getHeader("Authentication")).isNull();
        assertThat(response.getHeader("refresh")).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
