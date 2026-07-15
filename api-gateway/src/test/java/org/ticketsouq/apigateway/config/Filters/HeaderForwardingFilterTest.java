package org.ticketsouq.apigateway.config.Filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeaderForwardingFilterTest {

    private FilterChain filterChain;
    private ArgumentCaptor<ServletRequest> requestCaptor;

    private HeaderForwardingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new HeaderForwardingFilter();
        filterChain = mock(FilterChain.class);
        requestCaptor = ArgumentCaptor.forClass(ServletRequest.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldInjectUserIdHeaderWhenAuthenticated() throws Exception {
        String userId = UUID.randomUUID().toString();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any());
        HttpServletRequest wrapped = (HttpServletRequest) requestCaptor.getValue();
        assertThat(wrapped.getHeader("X-User-Id")).isEqualTo(userId);
    }

    @Test
    void shouldNotInjectHeaderWhenNotAuthenticated() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any());
        HttpServletRequest wrapped = (HttpServletRequest) requestCaptor.getValue();
        assertThat(wrapped.getHeader("X-User-Id")).isNull();
    }

    @Test
    void shouldNotInjectHeaderForAnonymousUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any());
        HttpServletRequest wrapped = (HttpServletRequest) requestCaptor.getValue();
        assertThat(wrapped.getHeader("X-User-Id")).isNull();
    }

    @Test
    void shouldNotInjectHeaderForNullPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(null, null, List.of()));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any());
        HttpServletRequest wrapped = (HttpServletRequest) requestCaptor.getValue();
        assertThat(wrapped.getHeader("X-User-Id")).isNull();
    }

    @Test
    void shouldOverrideExistingUserIdHeader() throws Exception {
        String userId = UUID.randomUUID().toString();
        request.addHeader("X-User-Id", "old-user-id");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any());
        HttpServletRequest wrapped = (HttpServletRequest) requestCaptor.getValue();
        assertThat(wrapped.getHeader("X-User-Id")).isEqualTo(userId);
    }

    @Test
    void shouldReturnUserIdInGetHeaders() throws Exception {
        String userId = UUID.randomUUID().toString();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any());
        HttpServletRequest wrapped = (HttpServletRequest) requestCaptor.getValue();

        java.util.Enumeration<String> headers = wrapped.getHeaders("X-User-Id");
        java.util.List<String> headerList = new java.util.ArrayList<>();
        while (headers.hasMoreElements()) headerList.add(headers.nextElement());
        assertThat(headerList).containsExactly(userId);
    }

    @Test
    void shouldIncludeUserIdInHeaderNames() throws Exception {
        String userId = UUID.randomUUID().toString();
        request.addHeader("Existing-Header", "value");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any());
        HttpServletRequest wrapped = (HttpServletRequest) requestCaptor.getValue();

        java.util.Enumeration<String> names = wrapped.getHeaderNames();
        java.util.List<String> nameList = new java.util.ArrayList<>();
        while (names.hasMoreElements()) nameList.add(names.nextElement());
        assertThat(nameList).contains("X-User-Id", "Existing-Header");
    }
}
