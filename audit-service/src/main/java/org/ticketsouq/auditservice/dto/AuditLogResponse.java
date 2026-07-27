package org.ticketsouq.auditservice.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        String action,
        String madeByEmail,
        String reason,
        Instant madeAt
) {}
