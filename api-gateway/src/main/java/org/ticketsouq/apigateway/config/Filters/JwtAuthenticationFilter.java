package org.ticketsouq.apigateway.config.Filters;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ticketsouq.apigateway.dto.AuthResponse;
import org.ticketsouq.apigateway.service.AuthService;
import org.ticketsouq.apigateway.service.AuthTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_HEADER = "Authentication";
    private static final String REFRESH_TOKEN_HEADER = "Refresh";

    private final AuthTokenService authTokenService;
    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String accessToken = extractAccessToken(request);
        String refreshToken = extractRefreshToken(request);

        if (accessToken != null) {
            try {
                if (authTokenService.isAccessTokenValid(accessToken)) {
                    authenticate(accessToken, request);
                    chain.doFilter(request, response);
                    return;
                }
            } catch (Exception e) {
                log.debug("Access token validation failed: {}", e.getMessage());
            }
        }

        if (refreshToken != null) {
            handleRefreshFlow(refreshToken, accessToken, request, response);
        }

        chain.doFilter(request, response);
    }

    private void handleRefreshFlow(String refreshToken, String accessToken, HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthResponse newTokens = authService.refresh(refreshToken);
            response.setHeader(ACCESS_TOKEN_HEADER, newTokens.access());
            response.setHeader(REFRESH_TOKEN_HEADER, newTokens.refresh());
            authenticate(newTokens.access(), request);
            log.debug("Tokens refreshed successfully");
        } catch (Exception e) {
            log.debug("Token refresh failed: {}", e.getMessage());
            if (accessToken != null) {
                try {
                    authenticate(accessToken, request);
                } catch (Exception ex) {
                    log.debug("Access token authentication also failed: {}", ex.getMessage());
                }
            }
        }
    }

    private String extractAccessToken(HttpServletRequest request) {
        String header = request.getHeader(ACCESS_TOKEN_HEADER);
        if (!StringUtils.hasText(header)) return null;
        return header.trim();
    }

    private String extractRefreshToken(HttpServletRequest request) {
        String header = request.getHeader(REFRESH_TOKEN_HEADER);
        if (!StringUtils.hasText(header)) return null;
        return header.trim();
    }

    private void authenticate(String token, HttpServletRequest request) {

        Claims claims = authTokenService.parseToken(token);
        String userId = claims.getSubject();

        List<String> roles = (List<String>) claims.get("roles");

        List<SimpleGrantedAuthority> authorities = (roles == null)
            ? List.of()
            : roles.stream()
            .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
            .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated user {} with roles {}", userId, roles);
    }
}
