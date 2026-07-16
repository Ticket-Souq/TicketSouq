package org.ticketsouq.auditservice.service;

import org.ticketsouq.auditservice.dto.AuditLogResponse;
import org.ticketsouq.auditservice.dto.CreateAuditLogRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditService {

    void produceEvent(CreateAuditLogRequest request);

    AuditLogResponse findById(UUID id);

    List<AuditLogResponse> findByMadeById(UUID madeById);

    List<AuditLogResponse> findByAction(String action);

    List<AuditLogResponse> findByDateRange(Instant from, Instant to);

    List<AuditLogResponse> findAll();
}
