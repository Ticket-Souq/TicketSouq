package org.ticketsouq.auditservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.auditservice.dto.AuditLogResponse;
import org.ticketsouq.auditservice.exception.AuditLogNotFoundException;
import org.ticketsouq.auditservice.mapper.AuditLogMapper;
import org.ticketsouq.auditservice.repository.AuditLogRepository;
import org.ticketsouq.auditservice.service.AuditService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;

    @Override
    public AuditLogResponse findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new AuditLogNotFoundException(id));
    }

    @Override
    public List<AuditLogResponse> findByMadeById(UUID madeById) {
        return repository.findByMadeByIdOrderByMadeAtDesc(madeById)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<AuditLogResponse> findByAction(String action) {
        return repository.findByActionOrderByMadeAtDesc(action)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<AuditLogResponse> findByDateRange(Instant from, Instant to) {
        return repository.findByMadeAtBetweenOrderByMadeAtDesc(from, to)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<AuditLogResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
