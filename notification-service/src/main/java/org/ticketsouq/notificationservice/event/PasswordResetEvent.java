package org.ticketsouq.notificationservice.event;

import java.util.UUID;

public record PasswordResetEvent(
    UUID userId,
    String token
) {
}
