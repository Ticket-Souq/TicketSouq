package org.ticketsouq.sharedmodule.EventService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

import java.util.List;
import java.util.UUID;

public class SeatNotInEventException extends BadRequestException {


    public SeatNotInEventException(List<UUID> seatIds) {
        super("Seats do not belong to event: " + seatIds);

    }

}
