package org.ticketsouq.sharedmodule.ApiGateway.event;

import java.util.UUID;

public record PasswordChangedEvent(
    UUID messageId,
    UUID userId
) {
}
