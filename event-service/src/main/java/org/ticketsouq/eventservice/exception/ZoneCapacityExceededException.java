package org.ticketsouq.eventservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

public class ZoneCapacityExceededException extends BadRequestException {

    public static final String REASON_CODE = "ZONE_CAPACITY_EXCEEDED";

    private final int available;

    public ZoneCapacityExceededException(int available) {
        super("Zone capacity exceeded. Available: " + available);
        this.available = available;
    }

    public int getAvailable() {
        return available;
    }

    public String getReasonCode() {
        return REASON_CODE;
    }
}
