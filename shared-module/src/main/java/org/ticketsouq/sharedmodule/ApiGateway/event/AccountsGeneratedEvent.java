package org.ticketsouq.sharedmodule.ApiGateway.event;

import java.util.List;
import java.util.UUID;

public record AccountsGeneratedEvent(
        UUID messageId,
        UUID orgHeadUserId,
        List<AccountInfo> accounts
) {
    public record AccountInfo(UUID userId, String email, String password, String role) {}
}
