package org.ticketsouq.venueservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "venue_templates")
@Getter
@Setter
@NoArgsConstructor
@SQLRestriction("deleted = false")
public class VenueTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @JoinColumn(name = "venue_id")
    @ManyToOne
    Venue venue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    String layout;

    @Column(name = "deleted", nullable = false)
    boolean deleted = false;
}
