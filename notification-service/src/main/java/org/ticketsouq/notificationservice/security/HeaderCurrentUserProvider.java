package org.ticketsouq.notificationservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("prod")
public class HeaderCurrentUserProvider implements CurrentUserProvider {

    private final HttpServletRequest request;

    public HeaderCurrentUserProvider(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public UUID getCurrentUserId() {
        return UUID.fromString(
            request.getHeader("X-User-Id")
        );
    }
}
