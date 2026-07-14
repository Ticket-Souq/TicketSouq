package org.ticketsouq.eventservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;

import java.time.Instant;
import java.time.LocalDateTime;
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

    @Column(name = "venue_template_id")
    private UUID venueTemplateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private EventCategory eventCategory;

    @Column(name = "organization")
    private String organization;

    @Column(name = "createdBy_id")
    private UUID createdBy;

    @Column(nullable = true) //TODO return this
    private String PosterUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingModel bookingModel;

    @Column(name = "start_date_time", nullable = true)
    private Instant startDate;

    @Column(name = "end_date_time", nullable = true)
    private Instant finishDate;

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY,cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Section> sections;

    @CreatedDate
    private LocalDateTime createdAt;
}
