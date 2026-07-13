package org.ticketsouq.sharedmodule.ApiGateway.event;

import java.util.UUID;

public record PasswordResetEvent(
    UUID messageId,
    UUID userId,
    String email,
    String token) {}
