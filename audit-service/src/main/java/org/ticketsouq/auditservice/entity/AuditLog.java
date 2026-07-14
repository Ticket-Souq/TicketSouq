package org.ticketsouq.auditservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_made_by", columnList = "madeById"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_made_at", columnList = "madeAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false)
    private UUID madeById;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant madeAt;

        public static AuditLog create(String action, UUID madeById, String reason, Instant madeAt) {
        return new AuditLog(null, action, madeById, reason, madeAt);
        }
}
