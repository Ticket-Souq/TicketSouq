package org.ticketsouq.eventservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "venue_id_temp")
    private UUID venueId_temp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private EventCategory event_categories;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "createdBy_id")
    private UUID createdBy;

    @Column(nullable = false)
    private String PosterUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingModel bookingModel;

    @Column(name = "start_date_time", nullable = false)
    private Instant startDate;

    @Column(name = "end_date_time", nullable = false)
    private Instant finishDate;

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<Section> sections = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;
}
