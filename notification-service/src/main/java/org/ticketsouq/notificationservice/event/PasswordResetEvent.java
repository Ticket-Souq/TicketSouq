package org.ticketsouq.notificationservice.event;

import java.util.UUID;

public record PasswordResetEvent(
    UUID messageId,
    UUID userId,
    String token
) {
}
