package org.ticketsouq.eventservice.dto;

import org.ticketsouq.eventservice.model.enums.SeatStatus;

public record UpdateSeatStatusRequest(
    SeatStatus status
) {
}
