package org.ticketsouq.eventservice.exception;

import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

public class InvalidEventTypeException extends BadRequestException {

    public static final String REASON_CODE = "INVALID_EVENT_TYPE";

    private final BookingModel expected;

    public InvalidEventTypeException(BookingModel expected) {
        super("Expected event type: " + expected);
        this.expected = expected;
    }

    public BookingModel getExpected() {
        return expected;
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
