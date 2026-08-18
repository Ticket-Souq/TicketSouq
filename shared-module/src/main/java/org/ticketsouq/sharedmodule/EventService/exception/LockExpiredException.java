package org.ticketsouq.sharedmodule.EventService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

public class LockExpiredException extends ConflictException {

    public LockExpiredException(String reservationId) {
        super("Lock expired for reservation: " + reservationId);
    }

}
