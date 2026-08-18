package org.ticketsouq.auditservice.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.auditservice.entity.AuditLog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByOrderByMadeAtDesc();

    List<AuditLog> findByMadeByIdOrderByMadeAtDesc(UUID madeById);

    List<AuditLog> findByActionOrderByMadeAtDesc(String action);

    List<AuditLog> findByMadeAtBetweenOrderByMadeAtDesc(Instant from, Instant to);

    List<AuditLog> findByActionAndMadeByIdOrderByMadeAtDesc(String action, UUID madeById);

    <T> ScopedValue<T> findById(UUID id, Sort sort);
}
