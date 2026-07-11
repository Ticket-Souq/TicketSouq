package org.ticketsouq.venueservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
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
}
