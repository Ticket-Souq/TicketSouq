package org.ticketsouq.eventservice.dto;


import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.util.UUID;

public record SeatResponse(

    UUID id,

    UUID sectionId,

    SeatStatus status

) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
            seat.getId(),
            seat.getSection().getId(),
            seat.getStatus()
        );
    }
}
