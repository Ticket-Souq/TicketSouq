package org.ticketsouq.apigateway.config.Filters;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Attempts to extract a JWT access token from the {@code Authorization: Bearer ...}
 * header and, if valid, populates the Spring Security context with the user's identity
 * and roles.
 * <p>
 * This filter is {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE passive}
 * — it never blocks a request. If the token is missing, expired, or invalid, the
 * request continues as anonymous. Downstream filters and controllers decide whether
 * authentication is required.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            authenticate(token, request);
        } catch (Exception e) {
            log.debug("Failed to authenticate token: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    /**
     * Pulls the bearer token from the Authorization header.
     * Returns null if the header is missing, doesn't start with "Bearer ",
     * or if the token part is blank.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!StringUtils.hasText(token)) return null;
        return token;
    }

    /**
     * Validates the access token and, if it checks out, builds a Spring Security
     * {@link UsernamePasswordAuthenticationToken} with the user's ID as the
     * principal and their roles as granted authorities.
     * <p>
     * Skips silently if:
     * - The security context already has an authentication (already logged in)
     * - The token is expired, revoked, or not an access token
     */
    private void authenticate(String token, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) return;

        if (!authTokenService.isAccessTokenValid(token)) return;

        Claims claims = authTokenService.parseToken(token);
        String userId = claims.getSubject();

        @SuppressWarnings("unchecked")
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
