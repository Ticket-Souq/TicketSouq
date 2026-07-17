package org.ticketsouq.eventservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

import java.util.UUID;

public class LockMismatchException extends ConflictException {

    public static final String REASON_CODE = "LOCK_MISMATCH";

    public LockMismatchException(UUID eventId, String reservationId) {
        super("Lock mismatch for reservation " + reservationId + " on event " + eventId);
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
