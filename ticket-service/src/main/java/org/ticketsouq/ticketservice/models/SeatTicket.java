package org.ticketsouq.ticketservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@DiscriminatorValue("SEAT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeatTicket extends Ticket {

    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "seat_row")
    private Integer row;

    @Column(name = "seat_number")
    private Integer seatNumber;

    private String category;
}
