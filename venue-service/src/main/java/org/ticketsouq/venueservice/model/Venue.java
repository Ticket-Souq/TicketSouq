package org.ticketsouq.venueservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@SQLRestriction("deleted = false")
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "org_id", nullable = false)
    UUID orgId;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "address", nullable = false)
    String address;

    @Enumerated(EnumType.STRING)
    Type type;

    @OneToMany(mappedBy = "venue")
    List<VenueTemplate> venueTemplates;

    @Column(name = "deleted", nullable = false)
    boolean deleted = false;
}
