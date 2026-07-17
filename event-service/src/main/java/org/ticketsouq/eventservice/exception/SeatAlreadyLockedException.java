package org.ticketsouq.eventservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

import java.util.List;
import java.util.UUID;

public class SeatAlreadyLockedException extends ConflictException {

    public static final String REASON_CODE = "SEAT_ALREADY_LOCKED";

    private final List<UUID> conflictingSeats;

    public SeatAlreadyLockedException(List<UUID> conflictingSeats) {
        super("Seats already locked: " + conflictingSeats);
        this.conflictingSeats = conflictingSeats;
    }

    public List<UUID> getConflictingSeats() {
        return conflictingSeats;
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
