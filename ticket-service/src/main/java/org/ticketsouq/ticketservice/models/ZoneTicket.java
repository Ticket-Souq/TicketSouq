package org.ticketsouq.ticketservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@DiscriminatorValue("ZONE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ZoneTicket extends Ticket {

    @Column(name = "section_id")
    private UUID sectionId;

    private String category;
}
