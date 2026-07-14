package org.ticketsouq.eventservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "event_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = true, unique = true) //TODO return this
    private String name;
}
