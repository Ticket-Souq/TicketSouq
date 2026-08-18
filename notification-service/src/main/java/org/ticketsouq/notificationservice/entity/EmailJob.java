package org.ticketsouq.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.ticketsouq.notificationservice.enums.EmailJobStatus;
import org.ticketsouq.notificationservice.enums.NotificationTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "email_jobs",
    indexes = {
        @Index(name = "idx_email_job_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EmailJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationTemplate template;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String variablesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailJobStatus status = EmailJobStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    private LocalDateTime lastAttemptAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
