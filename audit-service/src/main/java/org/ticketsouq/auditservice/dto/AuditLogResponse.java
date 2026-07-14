package org.ticketsouq.auditservice.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        UUID madeById,
        String reason,
        Instant madeAt
) {}
