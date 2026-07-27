package org.ticketsouq.auditservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.auditservice.client.userServiceClient;
import org.ticketsouq.auditservice.dto.AuditLogResponse;
import org.ticketsouq.auditservice.entity.AuditLog;
import org.ticketsouq.auditservice.repository.AuditLogRepository;
import org.ticketsouq.auditservice.service.AuditService;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.UserService.dto.UserEmail;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository repository;
    private final userServiceClient userServiceClient;

    @Override
    public AuditLogResponse findById(UUID id) {
        AuditLog entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Audit", id));
        return toResponse(entity);
    }

    @Override
    public List<AuditLogResponse> findByMadeById(UUID madeById) {
        return toResponses(repository.findByMadeByIdOrderByMadeAtDesc(madeById));
    }

    @Override
    public List<AuditLogResponse> findByAction(String action) {
        return toResponses(repository.findByActionOrderByMadeAtDesc(action));
    }

    @Override
    public List<AuditLogResponse> findByDateRange(Instant from, Instant to) {
        return toResponses(repository.findByMadeAtBetweenOrderByMadeAtDesc(from, to));
    }

    @Override
    public List<AuditLogResponse> findAll() {
        return toResponses(repository.findAll());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private AuditLogResponse toResponse(AuditLog entity) {
        return toResponses(List.of(entity)).getFirst();
    }

    private List<AuditLogResponse> toResponses(List<AuditLog> entities) {
        if (entities.isEmpty()) return List.of();

        List<UUID> uniqueIds = entities.stream()
            .map(AuditLog::getMadeById)
            .distinct()
            .toList();

        Map<UUID, String> emailMap = resolveEmails(uniqueIds);

        return entities.stream()
            .map(e -> new AuditLogResponse(
                e.getAction(),
                emailMap.get(e.getMadeById()),
                e.getReason(),
                e.getMadeAt()))
            .toList();
    }

    private Map<UUID, String> resolveEmails(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            List<UserEmail> emails = userServiceClient.getUsersEmails(ids);
            return emails.stream()
                .collect(Collectors.toMap(UserEmail::id, UserEmail::email));
        } catch (Exception e) {
            log.warn("Failed to resolve user emails via Feign: {}", e.getMessage());
            return Map.of();
        }
    }
}
