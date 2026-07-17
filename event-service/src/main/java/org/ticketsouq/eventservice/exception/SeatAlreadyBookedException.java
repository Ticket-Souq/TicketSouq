package org.ticketsouq.eventservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

import java.util.List;
import java.util.UUID;

public class SeatAlreadyBookedException extends ConflictException {

    public static final String REASON_CODE = "SEAT_ALREADY_BOOKED";

    private final List<UUID> conflictingSeats;

    public SeatAlreadyBookedException(List<UUID> conflictingSeats) {
        super("Seats already booked: " + conflictingSeats);
        this.conflictingSeats = conflictingSeats;
    }

    public List<UUID> getConflictingSeats() {
        return conflictingSeats;
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
