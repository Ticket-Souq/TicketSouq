package org.ticketsouq.notificationservice.event;

import java.util.UUID;

public record PasswordChangedEvent(
    UUID userId
) {
}
