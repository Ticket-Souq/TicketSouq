package org.ticketsouq.sharedmodule.ApiGateway.event;

import java.util.UUID;

public record PasswordResetEvent(
    UUID userId,
    String email,
    String token) {}
