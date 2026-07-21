package org.ticketsouq.reservationservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.ticketsouq.reservationservice.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Reservation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

//    private UUID zoneId;

//    private Integer quantity;

//    @ElementCollection
//    @CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
//    @Column(name = "seat_id")
//    private List<UUID> seatIds;

    @Column(nullable = false)
    private BigDecimal totalAmount;

//    @Column(nullable = false)
//    private LocalDateTime expiresAt;

//    private String failureReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

//    @Version
//    private Long version;
}
