package org.ticketsouq.notificationservice.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("local")
public class MockCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UUID getCurrentUserId() {
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }
}
