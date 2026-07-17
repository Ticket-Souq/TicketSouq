package org.ticketsouq.eventservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

import java.util.List;
import java.util.UUID;

public class SeatNotInEventException extends BadRequestException {

    public static final String REASON_CODE = "SEAT_NOT_IN_EVENT";

    private final List<UUID> seatIds;

    public SeatNotInEventException(List<UUID> seatIds) {
        super("Seats do not belong to event: " + seatIds);
        this.seatIds = seatIds;
    }

    public List<UUID> getSeatIds() {
        return seatIds;
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
