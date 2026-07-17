package org.ticketsouq.sharedmodule.EventService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

public class ZoneCapacityExceededException extends BadRequestException {


    public ZoneCapacityExceededException(int available) {
        super("Zone capacity exceeded. Available: " + available);

    }
}
