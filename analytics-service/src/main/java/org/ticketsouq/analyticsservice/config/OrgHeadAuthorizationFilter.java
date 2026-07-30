package org.ticketsouq.analyticsservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.ticketsouq.analyticsservice.model.EventAnalytics;
import org.ticketsouq.analyticsservice.repository.EventAnalyticsRepository;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrgHeadAuthorizationFilter extends OncePerRequestFilter {

    private final EventAnalyticsRepository eventAnalyticsRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            chain.doFilter(request, response);
            return;
        }

        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            chain.doFilter(request, response);
            return;
        }

        boolean isOrgHead = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ORG_HEAD"));
        if (!isOrgHead) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Access denied: ORG_HEAD role required");
            return;
        }

        String userId = auth.getName();
        String path = request.getRequestURI();

        if (path.contains("/api/v1/analytics/overview/")) {
            String orgId = request.getParameter("orgId");
            if (orgId == null || !eventAnalyticsRepository.existsByOrganizationIdAndCreatedBy(orgId, userId)) {
                response.sendError(HttpStatus.FORBIDDEN.value(), "Access denied: organization mismatch");
                return;
            }
        } else if (path.matches(".*/api/v1/analytics/events/[^/]+/(summary|sales-timeline)")) {
            String eventId = extractEventId(path);
            if (eventId == null) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid event path");
                return;
            }
            EventAnalytics event = eventAnalyticsRepository.findById(eventId).orElse(null);
            if (event == null) {
                response.sendError(HttpStatus.NOT_FOUND.value(), "Event not found");
                return;
            }
            if (!userId.equals(event.getCreatedBy())) {
                response.sendError(HttpStatus.FORBIDDEN.value(), "Access denied: you do not own this event");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String extractEventId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("events".equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return null;
    }
}
