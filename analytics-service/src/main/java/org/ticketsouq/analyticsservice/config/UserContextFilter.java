package org.ticketsouq.analyticsservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.ticketsouq.analyticsservice.client.UserServiceClient;
import org.ticketsouq.analyticsservice.dto.UserContextResponse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserServiceClient userServiceClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing X-User-Id header");
            return;
        }

        UserContextResponse context;
        try {
            context = userServiceClient.getUserContext(UUID.fromString(userId));
        } catch (Exception e) {
            log.error("Failed to fetch user context for {}: {}", userId, e.getMessage());
            response.sendError(HttpStatus.FORBIDDEN.value(), "Could not verify user identity");
            return;
        }

        if (context == null || context.role() == null) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "User has no organization role");
            return;
        }

        request.setAttribute("orgId", context.orgId());

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(context.role()));
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }
}
