package org.ticketsouq.sharedmodule.EventService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

import java.util.List;
import java.util.UUID;

public class SeatAlreadyBookedException extends ConflictException {


    public SeatAlreadyBookedException(List<UUID> conflictingSeats) {
        super("Seats already booked: " + conflictingSeats);
    }

}
