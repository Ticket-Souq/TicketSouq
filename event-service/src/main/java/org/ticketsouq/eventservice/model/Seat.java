package org.ticketsouq.eventservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID templateSeatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    @JsonIgnore
    private Section section;

    @Column(name = "lable", nullable = false)
    private String lable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
