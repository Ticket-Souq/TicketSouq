package org.ticketsouq.sharedmodule.EventService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

import java.util.List;
import java.util.UUID;

public class SeatAlreadyLockedException extends ConflictException {

    public SeatAlreadyLockedException(List<UUID> conflictingSeats) {
        super("Seats already locked: " + conflictingSeats);
    }

}
