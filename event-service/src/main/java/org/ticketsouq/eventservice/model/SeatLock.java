package org.ticketsouq.eventservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seat_locks", indexes = {
    @Index(name = "idx_seat_lock_expires", columnList = "expires_at"),
    @Index(name = "idx_seat_lock_reservation", columnList = "reservation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SeatLock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seat_id", nullable = false, unique = true)
    private UUID seatId;

    @Column(name = "reservation_id", nullable = false)
    private String reservationId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
