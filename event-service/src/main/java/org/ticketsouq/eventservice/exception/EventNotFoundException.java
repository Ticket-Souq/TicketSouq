package org.ticketsouq.eventservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

public class EventNotFoundException extends ResourceNotFoundException {

    public static final String REASON_CODE = "EVENT_NOT_FOUND";

    public EventNotFoundException(Object id) {
        super("Event", id);
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
