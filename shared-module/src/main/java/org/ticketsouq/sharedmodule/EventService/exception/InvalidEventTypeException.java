package org.ticketsouq.sharedmodule.EventService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

public class InvalidEventTypeException extends BadRequestException {

    public InvalidEventTypeException(String expected) {
        super("Expected event type: " + expected);
    }
}
