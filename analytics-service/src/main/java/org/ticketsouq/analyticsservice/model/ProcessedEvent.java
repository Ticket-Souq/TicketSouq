package org.ticketsouq.analyticsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "processed_events", uniqueConstraints = {
    @UniqueConstraint(name = "uq_processed_topic_msg", columnNames = {"topic", "message_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 80)
    private String topic;

    @Column(name = "message_id", nullable = false, length = 36)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
