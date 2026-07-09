package org.ticketsouq.notificationservice.event;



import java.util.UUID;

public record EmailVerificationEvent(
    UUID userId,
    String email,
    String token
) {
}
