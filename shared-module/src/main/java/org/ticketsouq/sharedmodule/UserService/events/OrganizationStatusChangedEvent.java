package org.ticketsouq.sharedmodule.UserService.events;

import java.util.UUID;

public record OrganizationStatusChangedEvent(
        UUID messageId,
        UUID orgHeadUserId,
        String orgHeadEmail,
        String organizationName,
        String status
) {
}
