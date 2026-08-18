package org.ticketsouq.sharedmodule.EventService.exception;

import lombok.Getter;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

@Getter
public class ZoneCapacityExceededException extends BadRequestException {

    private final int available;

    public ZoneCapacityExceededException(int available) {
        super("Zone capacity exceeded. Available: " + available);
        this.available = available;
    }
}
