package org.ticketsouq.sharedmodule.ReservationService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

public class ReservationExpiredException extends ConflictException {

    public static final String REASON_CODE = "RESERVATION_EXPIRED";

    private final String reservationId;

    public ReservationExpiredException(String reservationId) {
        super("Reservation expired: " + reservationId);
        this.reservationId = reservationId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
