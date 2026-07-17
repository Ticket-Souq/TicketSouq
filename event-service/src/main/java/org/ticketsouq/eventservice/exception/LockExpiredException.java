package org.ticketsouq.eventservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

public class LockExpiredException extends ConflictException {

    public static final String REASON_CODE = "LOCK_EXPIRED";

    private final String reservationId;

    public LockExpiredException(String reservationId) {
        super("Lock expired for reservation: " + reservationId);
        this.reservationId = reservationId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
