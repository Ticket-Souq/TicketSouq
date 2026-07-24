package org.ticketsouq.ticketservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSnapshot {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    private String title;
    private String description;
    private UUID venueTemplateId;
    private String organization;
    private String status;
    private String categoryName;
    private String posterUrl;
    private Instant startDate;
    private Instant finishDate;
}
