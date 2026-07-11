package org.ticketsouq.venueservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "venue_templates")
@Getter
@Setter
@NoArgsConstructor
public class VenueTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @JoinColumn(name = "venue_id")
    @OneToOne
    Venue venue;

    @Column(columnDefinition = "jsonb")
    String layout;
}
