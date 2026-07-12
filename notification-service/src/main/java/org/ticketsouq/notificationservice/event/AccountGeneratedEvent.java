package org.ticketsouq.notificationservice.event;

import java.util.List;
import java.util.UUID;

public record AccountGeneratedEvent(
    UUID messageId,
    UUID orgHeadUserId,
    List<AccountInfo> accounts
) {

    public record AccountInfo(
        UUID userId,
        String email,
        String password,
        String role
    ) {
    }
}
