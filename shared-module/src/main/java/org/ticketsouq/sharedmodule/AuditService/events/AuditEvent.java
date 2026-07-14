package org.ticketsouq.sharedmodule.AuditService.events;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        String action,
        UUID madeById,
        String reason,
        Instant madeAt
) {}
