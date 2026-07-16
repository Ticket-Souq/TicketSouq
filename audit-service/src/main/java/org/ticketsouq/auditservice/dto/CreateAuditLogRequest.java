package org.ticketsouq.auditservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateAuditLogRequest(
        @NotBlank String action,
        @NotNull UUID madeById,
        String reason,
        Instant madeAt
) {}
